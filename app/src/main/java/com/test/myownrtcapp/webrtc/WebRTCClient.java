package com.test.myownrtcapp.webrtc;

import android.os.Handler;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import androidx.annotation.Nullable;

import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.socketio.client.IO;
import com.github.nkzawa.socketio.client.Socket;
import com.test.myownrtcapp.webrtc.util.AppRTCUtils;


import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

/**
 * Negotiates signaling for chatting with https://appr.tc "rooms".
 * Uses the client<->server specifics of the apprtc AppEngine webapp.
 *
 * <p>To use: create an instance of this object (registering a message handler) and
 * call connectToRoom().  Once room connection is established
 * onConnectedToRoom() callback with room parameters is invoked.
 * Messages to other party (with local Ice candidates and answer SDP) can
 * be sent after WebSocket connection is established.
 */
public class WebRTCClient implements AppRTCClient {
    private static final String TAG = "WSRTCClient";

    private enum ConnectionState {NEW, CONNECTED, CLOSED, ERROR}

    private final Handler handler;
    private boolean initiator;
    private SignalingEvents events;
    private ConnectionState roomState;
    private RoomConnectionParameters connectionParameters;
    private Socket clientSocketIO;

    MessageHandler messageHandler;

    public WebRTCClient(SignalingEvents events) {
        this.events = events;
        roomState = ConnectionState.NEW;
        final HandlerThread handlerThread = new HandlerThread(TAG);
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
        messageHandler = new MessageHandler();

    }

    // --------------------------------------------------------------------
    // AppRTCClient interface implementation.
    // Asynchronously connect to an AppRTC room URL using supplied connection
    // parameters, retrieves room parameters and connect to WebSocket server.
    @Override
    public void connectToRoom(RoomConnectionParameters connectionParameters) {
        this.connectionParameters = connectionParameters;
        handler.post(new Runnable() {
            @Override
            public void run() {
                connectToRoomInternal();
            }
        });
    }

    @Override
    public void disconnectFromRoom() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                disconnectFromRoomInternal();
                handler.getLooper().quit();
            }
        });
    }

    boolean isRoomCreator = false;

    // Connects to room - function runs on a local looper thread.
    private void connectToRoomInternal() {

        roomState = ConnectionState.NEW;

        try {
            clientSocketIO = IO.socket(connectionParameters.roomUrl);
            clientSocketIO.connect();
            clientSocketIO.emit("join", connectionParameters.roomId);
            clientSocketIO.once("room_created", messageHandler.room_created);
            clientSocketIO.once("room_joined", messageHandler.onJoin);
            clientSocketIO.once("full_room", messageHandler.full_room);
            clientSocketIO.once("start_call", messageHandler.start_call);
            clientSocketIO.once("webrtc_offer", messageHandler.webrtc_offer);
            clientSocketIO.once("webrtc_answer", messageHandler.webrtc_answer);
            clientSocketIO.once("webrtc_ice_candidate", messageHandler.webrtc_ice_candidate);
            clientSocketIO.once("webrtc_leave", messageHandler.webrtc_leave);

        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
    }

    // Disconnect from room and send bye messages - runs on a local looper thread.
    private void disconnectFromRoomInternal() {
        Log.d(TAG, "Disconnect. Room state: " + roomState);
        if (roomState == ConnectionState.CONNECTED) {
            Log.d(TAG, "Closing room.");
            clientSocketIO.emit("webrtc_leave", connectionParameters.roomId);
        }
        roomState = ConnectionState.CLOSED;
        if (clientSocketIO != null) {
            clientSocketIO.disconnect();
        }
    }


    private void signalingParametersReady(final SignalingParameters signalingParameters) {
        initiator = signalingParameters.initiator;
        roomState = ConnectionState.CONNECTED;

    }

    // Send local offer SDP to the other participant.
    @Override
    public void sendOfferSdp(final SessionDescription sdp) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (roomState != ConnectionState.CONNECTED) {
                    reportError("Sending offer SDP in non connected state.");
                    return;
                }
                JSONObject json = new JSONObject();
                JSONObject jsonRes = new JSONObject();

                try {
                    json.put("sdp", sdp.description);
                    json.put("type", "offer");
                    jsonRes.put("sdp", json);
                    jsonRes.put("roomId", connectionParameters.roomId);
                    clientSocketIO.emit("webrtc_offer", jsonRes);
                } catch (Exception exp) {
                    exp.printStackTrace();
                }

                if (connectionParameters.loopback) {
                    // In loopback mode rename this offer to answer and route it back.
                    SessionDescription sdpAnswer = new SessionDescription(
                            SessionDescription.Type.fromCanonicalForm("answer"), sdp.description);
                    events.onRemoteDescription(sdpAnswer);
                }
            }
        });
    }

    // Send local answer SDP to the other participant.
    @Override
    public void sendAnswerSdp(final SessionDescription sdp) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (connectionParameters.loopback) {
                    Log.e(TAG, "Sending answer in loopback mode.");
                    return;
                }
                JSONObject json = new JSONObject();
                JSONObject jsonRes = new JSONObject();

                try {
                    json.put("sdp", sdp.description);
                    json.put("type", "answer");
                    jsonRes.put("sdp", json);
                    jsonRes.put("roomId", connectionParameters.roomId);
                    clientSocketIO.emit("webrtc_answer", jsonRes);

                } catch (Exception e) {
                    e.printStackTrace();
                }

            }
        });
    }

    // Send Ice candidate to the other participant.
    @Override
    public void sendLocalIceCandidate(final IceCandidate candidate) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                JSONObject json = new JSONObject();
                jsonPut(json, "type", "candidate");
                jsonPut(json, "label", candidate.sdpMLineIndex);
                jsonPut(json, "id", candidate.sdpMid);
                jsonPut(json, "candidate", candidate.sdp);
                jsonPut(json, "roomId", connectionParameters.roomId);

                if (initiator) {
                    // Call initiator sends ice candidates to GAE server.
                    if (roomState != ConnectionState.CONNECTED) {
                        reportError("Sending ICE candidate in non connected state.");
                        return;
                    }
                    try {
                        clientSocketIO.emit("webrtc_ice_candidate", json);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    if (connectionParameters.loopback) {
                        events.onRemoteIceCandidate(candidate);
                    }
                } else {

                    if (candidate instanceof IceCandidate) {
                        // Call receiver sends ice candidates to websocket server.
                        clientSocketIO.emit("webrtc_ice_candidate", json);
                    }
                }
            }
        });
    }

    // Send removed Ice candidates to the other participant.
    @Override
    public void sendLocalIceCandidateRemovals(final IceCandidate[] candidates) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                JSONObject json = new JSONObject();
                jsonPut(json, "type", "remove-candidates");
                JSONArray jsonArray = new JSONArray();
                for (final IceCandidate candidate : candidates) {
                    jsonArray.put(toJsonCandidate(candidate));
                }
                jsonPut(json, "candidates", jsonArray);
                if (initiator) {
                    // Call initiator sends ice candidates to GAE server.
                    if (roomState != ConnectionState.CONNECTED) {
                        reportError("Sending ICE candidate removals in non connected state.");
                        return;
                    }
                    // Added
                    try {
                        sendMessageViaSocketIO(json);
                    } catch (Exception exp) {
                        exp.toString();
                    }
                    if (connectionParameters.loopback) {
                        events.onRemoteIceCandidatesRemoved(candidates);
                    }
                } else {
                    // Call receiver sends ice candidates to websocket server.
                    clientSocketIO.send(json.toString());
                }
            }
        });
    }

    // --------------------------------------------------------------------
    // Helper functions.
    private void reportError(final String errorMessage) {
        Log.e(TAG, errorMessage);
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (roomState != ConnectionState.ERROR) {
                    roomState = ConnectionState.ERROR;
                    events.onChannelError(errorMessage);
                }
            }
        });
    }

    // Put a |key|->|value| mapping in |json|.
    private static void jsonPut(JSONObject json, String key, Object value) {
        try {
            json.put(key, value);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }


    public void sendMessageViaSocketIO(JSONObject payload) throws JSONException {
       /* JSONObject message = new JSONObject();
        message.put("to", to);
        message.put("type", type);
        message.put("payload", payload);*/
        try {
            clientSocketIO.emit("message", payload);
        } catch (Exception exp) {
            exp.printStackTrace();
        }
    }

    // Converts a Java candidate to a JSONObject.
    private JSONObject toJsonCandidate(final IceCandidate candidate) {
        JSONObject json = new JSONObject();
        jsonPut(json, "label", candidate.sdpMLineIndex);
        jsonPut(json, "id", candidate.sdpMid);
        jsonPut(json, "candidate", candidate.sdp);
        return json;
    }

    // Converts a JSON candidate to a Java object.
    IceCandidate toJavaCandidate(JSONObject json) throws JSONException {
        return new IceCandidate(
                "0", json.getInt("label"), json.getString("candidate"));
    }

    private class MessageHandler {

        private MessageHandler() {

        }

        private Emitter.Listener room_created = args -> {
            try {
                isRoomCreator = true;
            } catch (Exception exp) {
                exp.printStackTrace();
            }

        };
        private Emitter.Listener onJoin = args -> {
            String str = (String) args[0];
            isRoomCreator = false;
            try {
                clientSocketIO.emit("start_call", str);

            } catch (Exception exp) {
                exp.printStackTrace();
            }
        };

        private Emitter.Listener start_call = args -> {
            List<IceCandidate> iceCandidates = new ArrayList<>();

            try {
                if (isRoomCreator) {
                    SignalingParameters params = new SignalingParameters(
                            AppRTCUtils.getStunServers(), isRoomCreator, "", "", "", null, iceCandidates);
                    WebRTCClient.this.signalingParametersReady(params);
                    JSONObject json = new JSONObject();
                    jsonPut(json, "sdp", "offerSdp.description");
                    jsonPut(json, "roomId", connectionParameters.roomId);
                    jsonPut(json, "type", "'offer");
                    events.onConnectedToRoom(params, clientSocketIO, json);
                }

            } catch (Exception exp) {
                exp.printStackTrace();
            }
        };
        SessionDescription offerSdp = null;
        private Emitter.Listener webrtc_offer = args -> {
            List<IceCandidate> iceCandidates = null;

            JSONObject message = (JSONObject) args[0];
            String messageType;
            try {
                messageType = (String) message.get("type");
                if (messageType.equals("offer")) {
                    offerSdp = new SessionDescription(
                            SessionDescription.Type.fromCanonicalForm(messageType), message.getString("sdp"));


                } else if (messageType.equals("candidate")) {
                    IceCandidate candidate = new IceCandidate(
                            message.getString("id"), message.getInt("label"), message.getString("candidate"));
                    iceCandidates.add(candidate);
                } else {
                    Log.e(TAG, "Unknown message: " + message.toString());
                }

                if (!isRoomCreator) {

                    SignalingParameters params = new SignalingParameters(
                            AppRTCUtils.getStunServers(), isRoomCreator, "", "", "", offerSdp, iceCandidates);
                    WebRTCClient.this.signalingParametersReady(params);
                    JSONObject json = new JSONObject();
                    jsonPut(json, "sdp", offerSdp.description);
                    jsonPut(json, "roomId", connectionParameters.roomId);
                    jsonPut(json, "type", "'webrtc_answer");
                    events.onConnectedToRoom(params, clientSocketIO, json);
                }

            } catch (JSONException e) {
                e.printStackTrace();
            }

        };

        private Emitter.Listener webrtc_answer = args -> {
            SessionDescription answerSdp = null;
            try {
                JSONObject message = (JSONObject) args[0];
                String messageType;

                messageType = (String) message.get("type");
                if (messageType.equals("answer")) {
                    answerSdp = new SessionDescription(
                            SessionDescription.Type.fromCanonicalForm(messageType), message.getString("sdp"));
                    events.onRemoteDescription(answerSdp);

                } else {
                    Log.e(TAG, "Unknown message: " + message.toString());
                }

            } catch (Exception exp) {
                exp.printStackTrace();
            }
        };
        private Emitter.Listener full_room = args -> {
            String str = (String) args[0];
            try {
                System.out.println(str);
            } catch (Exception exp) {
                exp.printStackTrace();
            }
        };

        private Emitter.Listener webrtc_ice_candidate = args -> {
            List<IceCandidate> iceCandidates = null;
            iceCandidates = new ArrayList<>();

            JSONObject message = (JSONObject) args[0];
            try {

                if (message.getString("candidate") != null) {
                    IceCandidate candidate = new IceCandidate(
                            String.valueOf(message.getString("id")), message.getInt("label"), message.getString("candidate"));
                    iceCandidates.add(candidate);
                    System.out.println(message.toString());


                    SignalingParameters params = new SignalingParameters(
                            AppRTCUtils.getStunServers(), isRoomCreator, "", "", "", offerSdp, iceCandidates);

                    String ddd = message.getString("candidate");
                    events.onRemoteIceCandidate(toJavaCandidate(message));

                } else if (message.equals("remove-candidates")) {
                    JSONArray candidateArray = message.getJSONArray("candidates");
                    IceCandidate[] candidates = new IceCandidate[candidateArray.length()];
                    for (int i = 0; i < candidateArray.length(); ++i) {
                        candidates[i] = toJavaCandidate(candidateArray.getJSONObject(i));
                    }
                    events.onRemoteIceCandidatesRemoved(candidates);
                }
            } catch (Exception exp) {
                exp.printStackTrace();
            }
        };

        private Emitter.Listener webrtc_leave = args -> {
            try {
                disconnectFromRoomInternal();

            } catch (Exception exp) {
                exp.printStackTrace();
            }
        };
    }
}
