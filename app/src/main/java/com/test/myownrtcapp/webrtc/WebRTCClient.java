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
    private static final String ROOM_JOIN = "join";
    private static final String ROOM_MESSAGE = "message";
    private static final String ROOM_LEAVE = "leave";

    private enum ConnectionState { NEW, CONNECTED, CLOSED, ERROR }

    private enum MessageType { MESSAGE, LEAVE }

    private final Handler handler;
    private boolean initiator;
    private SignalingEvents events;
    private ConnectionState roomState;
    private RoomConnectionParameters connectionParameters;
    private String messageUrl;
    private String leaveUrl;
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
boolean isRoomCreator=false;
    // Connects to room - function runs on a local looper thread.
    private void connectToRoomInternal() {
       /* String connectionUrl = getConnectionUrl(connectionParameters);
        Log.d(TAG, "Connect to room: " + connectionUrl);*/
        roomState = ConnectionState.NEW;

        try {
            clientSocketIO = IO.socket(connectionParameters.roomUrl);
            clientSocketIO.connect();
            clientSocketIO.emit("join","amit");
            clientSocketIO.on("room_created", messageHandler.room_created);
            clientSocketIO.on("room_joined", messageHandler.onJoin);
            clientSocketIO.on("full_room", messageHandler.full_room);
            clientSocketIO.on("start_call", messageHandler.start_call);
            clientSocketIO.on("webrtc_offer", messageHandler.webrtc_offer);
            clientSocketIO.on("webrtc_answer", messageHandler.webrtc_answer);
            clientSocketIO.on("webrtc_ice_candidate", messageHandler.webrtc_ice_candidate);



        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
    }

    // Disconnect from room and send bye messages - runs on a local looper thread.
    private void disconnectFromRoomInternal() {
        Log.d(TAG, "Disconnect. Room state: " + roomState);
        if (roomState == ConnectionState.CONNECTED) {
            Log.d(TAG, "Closing room.");
            //sendPostMessage(MessageType.LEAVE, leaveUrl, null);
            //sendMessageViaSocketIO();
        }
        roomState = ConnectionState.CLOSED;
        if (clientSocketIO != null) {
            clientSocketIO.disconnect();
        }
    }

    // Helper functions to get connection, post message and leave message URLs
    private String getConnectionUrl(RoomConnectionParameters connectionParameters) {

        return connectionParameters.roomUrl + "/" + ROOM_JOIN + "/" + connectionParameters.roomId
                + getQueryString(connectionParameters);
    }

    private String getMessageUrl(
            RoomConnectionParameters connectionParameters, SignalingParameters signalingParameters) {
        return connectionParameters.roomUrl + "/" + ROOM_MESSAGE + "/" + connectionParameters.roomId
                + "/" + signalingParameters.clientId + getQueryString(connectionParameters);
    }

    private String getLeaveUrl(
            RoomConnectionParameters connectionParameters, SignalingParameters signalingParameters) {
        return connectionParameters.roomUrl + "/" + ROOM_LEAVE + "/" + connectionParameters.roomId + "/"
                + signalingParameters.clientId + getQueryString(connectionParameters);
    }

    private String getQueryString(RoomConnectionParameters connectionParameters) {
        if (connectionParameters.urlParameters != null) {
            return "?" + connectionParameters.urlParameters;
        } else {
            return "";
        }
    }

    // Callback issued when room parameters are extracted. Runs on local
    // looper thread.
  /*  private void signalingParametersReady(final SignalingParameters signalingParameters) {
        Log.d(TAG, "Room connection completed.");
        if (connectionParameters.loopback
                && (!signalingParameters.initiator || signalingParameters.offerSdp != null)) {
            reportError("Loopback room is busy.");
            return;
        }
        if (!connectionParameters.loopback && !signalingParameters.initiator
                && signalingParameters.offerSdp == null) {
            Log.w(TAG, "No offer SDP in room response.");
        }
        initiator = signalingParameters.initiator;
        messageUrl = getMessageUrl(connectionParameters, signalingParameters);
        leaveUrl = getLeaveUrl(connectionParameters, signalingParameters);
        Log.d(TAG, "Message URL: " + messageUrl);
        Log.d(TAG, "Leave URL: " + leaveUrl);
        roomState = ConnectionState.CONNECTED;

        // Fire connection and signaling parameters events.
        events.onConnectedToRoom(signalingParameters);

        // Connect and register WebSocket client.
        wsClient.connect(signalingParameters.wssUrl, signalingParameters.wssPostUrl);
        wsClient.register(connectionParameters.roomId, signalingParameters.clientId);
    }*/

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
                jsonPut(json, "sdp", sdp.description);
                jsonPut(json, "type", "offer");
                //sendPostMessage(MessageType.MESSAGE, messageUrl, json.toString());
                try {
                    sendMessageViaSocketIO(json);
                } catch (JSONException e) {
                    e.printStackTrace();
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
                jsonPut(json, "sdp", sdp.description);
              // jsonPut(json, "type", "answer");
                jsonPut(json, "type", "webrtc_answer");
                jsonPut(json, "roomId", "amit");

               // wsClient.send(json.toString());
                // TO DO amit
               // clientSocketIO.send(json.toString());

                //clientSocketIO.emit("webrtc_answer", json);

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
                //jsonPut(json, "type", "candidate");

              /*  jsonPut(json, "label", candidate.sdpMid);
                jsonPut(json, "candidate", candidate.sdp);*/
                jsonPut(json, "roomId", "amit");

                if (initiator) {
                    // Call initiator sends ice candidates to GAE server.
                    if (roomState != ConnectionState.CONNECTED) {
                        reportError("Sending ICE candidate in non connected state.");
                        return;
                    }
                    //sendPostMessage(MessageType.MESSAGE, messageUrl, json.toString());
                    try {
                        sendMessageViaSocketIO(json);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    try {
                        sendMessageViaSocketIO(json);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    if (connectionParameters.loopback) {
                        events.onRemoteIceCandidate(candidate);
                    }
                } else {

                    if(candidate instanceof  IceCandidate) {
                        // Call receiver sends ice candidates to websocket server.
                        // wsClient.send(json.toString());
                        //clientSocketIO.send(json.toString());
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
                    }
                    catch (Exception exp){
                        exp.toString();
                    }
                    ///added close

                   // sendPostMessage(MessageType.MESSAGE, messageUrl, json.toString());
                    if (connectionParameters.loopback) {
                        events.onRemoteIceCandidatesRemoved(candidates);
                    }
                } else {
                    // Call receiver sends ice candidates to websocket server.
                    //wsClient.send(json.toString());
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

  /*  // Send SDP or ICE candidate to a room server.
    private void sendPostMessage(
            final MessageType messageType, final String url, @Nullable final String message) {
        String logInfo = url;
        if (message != null) {
            logInfo += ". Message: " + message;
        }
        Log.d(TAG, "C->GAE: " + logInfo);

        AsyncHttpURLConnection httpConnection =
                new AsyncHttpURLConnection("POST", url, message, new AsyncHttpEvents() {
                    @Override
                    public void onHttpError(String errorMessage) {
                        reportError("GAE POST error: " + errorMessage);
                    }

                    @Override
                    public void onHttpComplete(String response) {
                        if (messageType == MessageType.MESSAGE) {
                            try {
                                JSONObject roomJson = new JSONObject(response);
                                String result = roomJson.getString("result");
                                if (!result.equals("SUCCESS")) {
                                    reportError("GAE POST error: " + result);
                                }
                            } catch (JSONException e) {
                                reportError("GAE POST JSON error: " + e.toString());
                            }
                        }
                    }
                });
        httpConnection.send();
    }*/
    public void sendMessageViaSocketIO( JSONObject payload) throws JSONException {
       /* JSONObject message = new JSONObject();
        message.put("to", to);
        message.put("type", type);
        message.put("payload", payload);*/
       try{
           clientSocketIO.emit("message", payload);
       }
       catch(Exception exp){
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

    private interface Command {
        void execute(String peerId, JSONObject payload) throws JSONException;
    }
    private class MessageHandler {
        private HashMap<String, Command> commandMap;

        private MessageHandler() {
          /*  this.commandMap = new HashMap<>();
            commandMap.put("init", new CreateOfferCommand());
            commandMap.put("offer", new CreateAnswerCommand());
            commandMap.put("answer", new SetRemoteSDPCommand());
            commandMap.put("candidate", new AddIceCandidateCommand());*/
        }

        private Emitter.Listener room_created = args -> {
            String str= (String) args[0];
            try {
                isRoomCreator=true;
                System.out.println(str);
            }
            catch(Exception exp){
                exp.printStackTrace();
            }

        };
        private Emitter.Listener onJoin = args -> {
            String str= (String) args[0];
            isRoomCreator=false;
            try {
                System.out.println(str);

               clientSocketIO.emit("start_call", str);

            }
            catch(Exception exp){
                exp.printStackTrace();
            }
        };

        private Emitter.Listener start_call = args -> {
            String str= (String) args[0];
            try {
                System.out.println(str);

                //clientSocketIO.emit("start_call", str);
            }
            catch(Exception exp){
                exp.printStackTrace();
            }
        };
        SessionDescription offerSdp = null;
boolean gotOffer;
        private Emitter.Listener webrtc_offer = args -> {
            List<IceCandidate> iceCandidates = null;

            JSONObject message= (JSONObject)args[0];
            String messageType;
            try {
                messageType= (String) message.get("type");
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

                if(!isRoomCreator){
                    gotOffer=true;
                    SignalingParameters params = new SignalingParameters(
                            AppRTCUtils.getStunServers(), isRoomCreator, "", "", "", offerSdp, iceCandidates);

                    JSONObject json = new JSONObject();
                    jsonPut(json, "sdp", offerSdp.description);
                    jsonPut(json, "roomId", "amit");
                    jsonPut(json, "type", "'webrtc_answer");
                    //clientSocketIO.emit("webrtc_answer", json.toString());
                    events.onConnectedToRoom(params,clientSocketIO,json);
                    //events.onRemoteDescription(offerSdp,clientSocketIO); //// Impotss
                }

            } catch (JSONException e) {
                e.printStackTrace();
            }

        };

        private Emitter.Listener webrtc_answer = args -> {
            String str= (String) args[0];
            try {
                System.out.println(str);
                //clientSocketIO.emit("start_call", str);
                //clientSocketIO.emit("start_call", str);
                //  rtcPeerConnection.setRemoteDescription(new RTCSessionDescription(event))
                events.onRemoteDescription(offerSdp);
            }
            catch(Exception exp){
                exp.printStackTrace();
            }
        };
        private Emitter.Listener full_room = args -> {
            String str= (String) args[0];
            try {
                System.out.println(str);

                //clientSocketIO.emit("start_call", str);
            }
            catch(Exception exp){
                exp.printStackTrace();
            }
        };
        int count=0;
boolean firstcall=false;

HashSet<String> serverCanditate = new HashSet<>();
        private Emitter.Listener webrtc_ice_candidate = args -> {
          //  String str= (String) args[0];
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
                    //   events.onConnectedToRoom(params);


                   String ddd = message.getString("candidate");

                 /*  if(!serverCanditate.contains(ddd.split(" ")[0]))
                   {*/
                       serverCanditate.add(ddd.split(" ")[0]);
                       events.onRemoteIceCandidate(toJavaCandidate(message));
                       count++;
                       Log.d(TAG, "Candidate sent from server: " + count);
                       Log.e(TAG, "No of Time webrtc_ice_candidate : " +count);

                   //}


                  /*  if(!firstcall && gotOffer&& count>5){
                        firstcall=true;
                        gotOffer=false;
                        if(!isRoomCreator){
                            JSONObject json = new JSONObject();
                            jsonPut(json, "sdp", offerSdp.description);
                            jsonPut(json, "roomId", "amit");
                            jsonPut(json, "type", "'webrtc_answer");
                            //clientSocketIO.emit("webrtc_answer", json.toString());
                            events.onConnectedToRoom(params,clientSocketIO,json);
                        }
                    }*/
                } else if (message.equals("remove-candidates")) {
                    JSONArray candidateArray = message.getJSONArray("candidates");
                    IceCandidate[] candidates = new IceCandidate[candidateArray.length()];
                    for (int i = 0; i < candidateArray.length(); ++i) {
                        candidates[i] = toJavaCandidate(candidateArray.getJSONObject(i));
                    }
                    events.onRemoteIceCandidatesRemoved(candidates);
                }
            }
            catch(Exception exp){
                exp.printStackTrace();
            }
        };




        private Emitter.Listener onMessage = new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                String msg= (String) args[0];
                try {
                    JSONObject json = (JSONObject) args[0];
                    String msgText = json.getString("msg");
                    String errorText = json.optString("error");
                    if (msgText.length() > 0) {
                        json = new JSONObject(msgText);
                        String type = json.optString("type");
                        if (type.equals("candidate")) {
                            events.onRemoteIceCandidate(toJavaCandidate(json));
                        } else if (type.equals("remove-candidates")) {
                            JSONArray candidateArray = json.getJSONArray("candidates");
                            IceCandidate[] candidates = new IceCandidate[candidateArray.length()];
                            for (int i = 0; i < candidateArray.length(); ++i) {
                                candidates[i] = toJavaCandidate(candidateArray.getJSONObject(i));
                            }
                            events.onRemoteIceCandidatesRemoved(candidates);
                        } else if (type.equals("answer")) {
                            if (initiator) {
                                SessionDescription sdp = new SessionDescription(
                                        SessionDescription.Type.fromCanonicalForm(type), json.getString("sdp"));
                                events.onRemoteDescription(sdp);
                            } else {
                                reportError("Received answer for call initiator: " + msg);
                            }
                        } else if (type.equals("offer")) {
                            if (!initiator) {
                                SessionDescription sdp = new SessionDescription(
                                        SessionDescription.Type.fromCanonicalForm(type), json.getString("sdp"));
                                events.onRemoteDescription(sdp);
                            } else {
                                reportError("Received offer for call receiver: " + msg);
                            }
                        } else if (type.equals("bye")) {
                            events.onChannelClose();
                        } else {
                            reportError("Unexpected WebSocket message: " + msg);
                        }
                    } else {
                        if (errorText != null && errorText.length() > 0) {
                            reportError("WebSocket error message: " + errorText);
                        } else {
                            reportError("Unexpected WebSocket message: " + msg);
                        }
                    }
                } catch (JSONException e) {
                    reportError("WebSocket message JSON parsing error: " + e.toString());
                }
            }
        };
            private Emitter.Listener onId = new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                String id = (String) args[0];
               // mListener.onCallReady(id);
            }
        };
    }
}
