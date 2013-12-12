/*
 * Copyright (C) 2006 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.content;

import android.app.ProcessRecord;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.PrintWriterPrinter;
import android.util.TimeUtils;

import java.io.PrintWriter;
import java.util.List;

/**
 * An active intent broadcast.
 */
public class BroadcastRecord extends Binder {
    final public  Intent intent;    // the original intent that generated us
    final public  ProcessRecord callerApp; // process that sent this
    final public  String callerPackage; // who sent this
    final public  int callingPid;   // the pid of who sent this
    final public  int callingUid;   // the uid of who sent this
    final public  boolean ordered;  // serialize the send to receivers?
    final public  boolean sticky;   // originated from existing sticky data?
    final public  boolean initialSticky; // initial broadcast from register to sticky?
    final public  String requiredPermission; // a permission the caller has required
    final public List receivers;   // contains BroadcastFilter and ResolveInfo
    public long dispatchTime;      // when dispatch started on this set of receivers
    public long receiverTime;      // when current receiver started for timeouts.
    public long finishTime;        // when we finished the broadcast.
    public int resultCode;         // current result code value.
    public String resultData;      // current result data value.
    public Bundle resultExtras;    // current result extra data values.
    public boolean resultAbort;    // current result abortBroadcast value.
    public int nextReceiver;       // next receiver to be executed.
    public IBinder receiver;       // who is currently running, null if none.
    public int state;
    public int anrCount;           // has this broadcast record hit any ANRs?

    static final int IDLE = 0;
    static final int APP_RECEIVE = 1;
    static final int CALL_IN_RECEIVE = 2;
    static final int CALL_DONE_RECEIVE = 3;

    // The following are set when we are calling a receiver (one that
    // was found in our list of registered receivers).
    BroadcastFilter curFilter;

    // The following are set only when we are launching a receiver (one
    // that was found by querying the package manager).
    ProcessRecord curApp;       // hosting application of current receiver.
    ComponentName curComponent; // the receiver class that is currently running.
    ActivityInfo curReceiver;   // info about the receiver that is currently running.

    void dump(PrintWriter pw, String prefix) {
        final long now = SystemClock.uptimeMillis();

        pw.print(prefix); pw.println(this);
        pw.print(prefix); pw.println(intent);
        if (sticky) {
            Bundle bundle = intent.getExtras();
            if (bundle != null) {
                pw.print(prefix); pw.print("extras: "); pw.println(bundle.toString());
            }
        }
        pw.print(prefix); pw.print("caller="); pw.print(callerPackage); pw.print(" ");
                pw.print(callerApp != null ? callerApp.toShortString() : "null");
                pw.print(" pid="); pw.print(callingPid);
                pw.print(" uid="); pw.println(callingUid);
        if (requiredPermission != null) {
            pw.print(prefix); pw.print("requiredPermission="); pw.println(requiredPermission);
        }
        pw.print(prefix); pw.print("dispatchTime=");
                TimeUtils.formatDuration(dispatchTime, now);
        if (finishTime != 0) {
            pw.print(" finishTime="); TimeUtils.formatDuration(finishTime, now);
        } else {
            pw.print(" receiverTime="); TimeUtils.formatDuration(receiverTime, now);
        }
        pw.println("");
        if (anrCount != 0) {
            pw.print(prefix); pw.print("anrCount="); pw.println(anrCount);
        }
        if (resultCode != -1 || resultData != null) {
            pw.print(prefix); pw.print("resultTo="); 
                    pw.print(" resultCode="); pw.print(resultCode);
                    pw.print(" resultData="); pw.println(resultData);
        }
        if (resultExtras != null) {
            pw.print(prefix); pw.print("resultExtras="); pw.println(resultExtras);
        }
        if (resultAbort || ordered || sticky || initialSticky) {
            pw.print(prefix); pw.print("resultAbort="); pw.print(resultAbort);
                    pw.print(" ordered="); pw.print(ordered);
                    pw.print(" sticky="); pw.print(sticky);
                    pw.print(" initialSticky="); pw.println(initialSticky);
        }
        if (nextReceiver != 0 || receiver != null) {
            pw.print(prefix); pw.print("nextReceiver="); pw.print(nextReceiver);
                    pw.print(" receiver="); pw.println(receiver);
        }
        if (curFilter != null) {
            pw.print(prefix); pw.print("curFilter="); pw.println(curFilter);
        }
        if (curReceiver != null) {
            pw.print(prefix); pw.print("curReceiver="); pw.println(curReceiver);
        }
        if (curApp != null) {
            pw.print(prefix); pw.print("curApp="); pw.println(curApp);
            pw.print(prefix); pw.print("curComponent=");
                    pw.println((curComponent != null ? curComponent.toShortString() : "--"));
            if (curReceiver != null && curReceiver.applicationInfo != null) {
                pw.print(prefix); pw.print("curSourceDir=");
                        pw.println(curReceiver.applicationInfo.sourceDir);
            }
        }
        String stateStr = " (?)";
        switch (state) {
            case IDLE:              stateStr=" (IDLE)"; break;
            case APP_RECEIVE:       stateStr=" (APP_RECEIVE)"; break;
            case CALL_IN_RECEIVE:   stateStr=" (CALL_IN_RECEIVE)"; break;
            case CALL_DONE_RECEIVE: stateStr=" (CALL_DONE_RECEIVE)"; break;
        }
        pw.print(prefix); pw.print("state="); pw.print(state); pw.println(stateStr);
        final int N = receivers != null ? receivers.size() : 0;
        String p2 = prefix + "  ";
        PrintWriterPrinter printer = new PrintWriterPrinter();
        for (int i=0; i<N; i++) {
            Object o = receivers.get(i);
            pw.print(prefix); pw.print("Receiver #"); pw.print(i);
                    pw.print(": "); pw.println(o);
            if (o instanceof BroadcastFilter)
                ((BroadcastFilter)o).dumpBrief(pw, p2);
            else if (o instanceof ResolveInfo)
                ((ResolveInfo)o).dump(printer, p2);
        }
    }

    public BroadcastRecord(Intent _intent, ProcessRecord _callerApp, String _callerPackage,
            int _callingPid, int _callingUid, String _requiredPermission,
            List _receivers, int _resultCode,
            String _resultData, Bundle _resultExtras, boolean _serialized,
            boolean _sticky, boolean _initialSticky) {

        intent = _intent;
        callerApp = _callerApp;
        callerPackage = _callerPackage;
        callingPid = _callingPid;
        callingUid = _callingUid;
        requiredPermission = _requiredPermission;
        receivers = _receivers;
        resultCode = _resultCode;
        resultData = _resultData;
        resultExtras = _resultExtras;
        ordered = _serialized;
        sticky = _sticky;
        initialSticky = _initialSticky;
        nextReceiver = 0;
        state = IDLE;
    }

    public String toString() {
        return "BroadcastRecord{"
           // + Integer.toHexString(System.identityHashCode(this))
            + " " + intent.getAction() + "}";
    }
}