/*
 *  Copyright 2014 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package com.test.myownrtcapp.webrtc.util;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.webrtc.PeerConnection;

import java.util.ArrayList;
import java.util.List;

/**
 * AppRTCUtils provides helper functions for managing thread safety.
 */
public final class AppRTCUtils {
    private AppRTCUtils() {
    }

    /**
     * Helper method which throws an exception  when an assertion has failed.
     */
    public static void assertIsTrue(boolean condition) {
        if (!condition) {
            throw new AssertionError("Expected condition to be true");
        }
    }

    /**
     * Helper method for building a string of thread information.
     */
    public static String getThreadInfo() {
        return "@[name=" + Thread.currentThread().getName() + ", id=" + Thread.currentThread().getId()
                + "]";
    }

    /**
     * Information about the current build, taken from system properties.
     */
    public static void logDeviceInfo(String tag) {
        Log.d(tag, "Android SDK: " + Build.VERSION.SDK_INT + ", "
                + "Release: " + Build.VERSION.RELEASE + ", "
                + "Brand: " + Build.BRAND + ", "
                + "Device: " + Build.DEVICE + ", "
                + "Id: " + Build.ID + ", "
                + "Hardware: " + Build.HARDWARE + ", "
                + "Manufacturer: " + Build.MANUFACTURER + ", "
                + "Model: " + Build.MODEL + ", "
                + "Product: " + Build.PRODUCT);
    }


    public static List<PeerConnection.IceServer> getStunServers() {

        String[] stun_servers = {"stun:stun.l.google.com:19302"/*,
                "stun:64.233.161.127:19305",
                "stun:[2A00:1450:4010:C01::7F]:19302",
                "stun:stun1.l.google.com:19302", "stun:stun2.l.google.com:19302",
                "stun:stun3.l.google.com:19302", "stun:stun4.l.google.com:19302"*/};

        List<PeerConnection.IceServer> iceServers = new ArrayList<>();
       // iceServers.add(new PeerConnection.IceServer("stun:stun.l.google.com:19302"));

        PeerConnection.IceServer turnServer;
        for (String stun_server : stun_servers) {
            turnServer =
                    PeerConnection.IceServer.builder(stun_server)
                            .setUsername("")
                            .setPassword("")
                            .setTlsCertPolicy(PeerConnection.TlsCertPolicy.TLS_CERT_POLICY_INSECURE_NO_CHECK)
                            .createIceServer();
            iceServers.add(turnServer);
        }
//41784574&key=4080218913

        return iceServers;


    }

  public static   boolean checkAndRequestPermissions(Activity ctx) {
    int readPhone_state= ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_PHONE_STATE);
    int camera=ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA);
    List<String> listPermissionsNeeded = new ArrayList<>();
    if (camera != PackageManager.PERMISSION_GRANTED) {
      listPermissionsNeeded.add(Manifest.permission.CAMERA);
    }
    if (readPhone_state != PackageManager.PERMISSION_GRANTED) {
      listPermissionsNeeded.add(android.Manifest.permission.READ_PHONE_STATE);
    }

    if (!listPermissionsNeeded.isEmpty()) {
      ActivityCompat.requestPermissions(ctx, listPermissionsNeeded.toArray(new String[listPermissionsNeeded.size()]),1);
      return false;
    }
    return true;
  }
}
