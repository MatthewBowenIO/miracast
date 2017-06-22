package com.squirrels.miracast;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by matthewbowen on 11/28/16.
 */

/**
 * Extension of the BroadcastReceiver class to capture actions needed for Miracast connections.
 *
 * Implementation requirments:
 * Create and IntentFiler to ensure that the receiver only handles Miracast related Intents.
 *
 *      IntentFilter intentFilter = new IntentFilter();
 *      intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
 *      intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
 *      intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
 *      intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
 *
 * Register the receiver onCreate() and onResume() using the IntentFilter.
 *
 *      registerReceiver(broadcastReceiver, intentFilter);
 *
 * Unregister the receiver onPause()
 *
 *      unregisterReceiver(broadcastReceiver);
 */
public class ReflectorMiracastReceiver extends BroadcastReceiver {
    private static String TAG = "Miracast";

    private WifiP2pManager manager;
    private WifiP2pManager.Channel channel;
    private Context context;

    private Timer mArpTableObservationTimer;
    private int mArpRetryCount = 0;
    private final int MAX_ARP_RETRY_COUNT = 60;
    private int mP2pControlPort = -1;
    private String mP2pInterfaceName;

    private MiracastDeviceListener mMiracastDeviceListener;

    /**
     *
     * @param context: Application Context is used to obtain the WifiP2pManager and associated
     *                 Channel.
     * @param listener: MiracastDeviceListener is called to notify the client when a device is
     *                  trying to connect or disconnect.
     */
    public ReflectorMiracastReceiver(Context context, MiracastDeviceListener listener) {
        super();

        this.manager = (WifiP2pManager) context.getSystemService(Context.WIFI_P2P_SERVICE);
        this.channel = manager.initialize(context, context.getMainLooper(), null);
        this.context= context;

        this.mMiracastDeviceListener = listener;
    }

    /**
     * Register this receiver as a Miracast BroadcastReceiver with correct IntentFilters.
     */
    public void RegisterAsRecevier() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

        this.context.registerReceiver(this, intentFilter);
    }

    /**
     * Unregister this receiver as a Miracast BroadcastReceiver.
     */
    public void UnregisterAsReceiver() {
        this.context.unregisterReceiver(this);
    }

    @Override
    public void onReceive(final Context context, Intent intent) {
        String action = intent.getAction();
        if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {
        } else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {
        } else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
            NetworkInfo networkInfo = (NetworkInfo) intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);

            if(networkInfo.isConnected())
                invokeSink();
        } else if (WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(action)) {
        }
    }

    class ArpTableObservationTask extends TimerTask {
        @Override
        public void run() {
            RarpImpl rarp = new RarpImpl();
            String source_ip = rarp.execRarp(mP2pInterfaceName);

            if (source_ip == null) {
                Log.d(TAG, "retry:" + mArpRetryCount);
                if (++mArpRetryCount > MAX_ARP_RETRY_COUNT) {
                    mArpTableObservationTimer.cancel();
                    return;
                }
                return;
            }

            mArpTableObservationTimer.cancel();
            if(mMiracastDeviceListener != null)
                mMiracastDeviceListener.AddMiracastDevice(source_ip, mP2pControlPort);
        }
    }

    private void invokeSink() {
        manager.requestGroupInfo(channel, new WifiP2pManager.GroupInfoListener() {
            public void onGroupInfoAvailable(WifiP2pGroup group) {
                if (group == null) {
                    return;
                }

                mP2pControlPort = -1;
                // Miracast device filtering
                Collection<WifiP2pDevice> p2pdevs = group.getClientList();
                for (WifiP2pDevice dev : p2pdevs) {
                    boolean b = isWifiDisplaySource(dev);
                    if (!b) {
                        continue;
                    }
                }
                if (mP2pControlPort == -1) {
                    mP2pControlPort = 7236;
                }

                // connect
                if (group.isGroupOwner()) {
                    mP2pInterfaceName = group.getInterface();

                    mArpTableObservationTimer = new Timer();
                    ArpTableObservationTask task = new ArpTableObservationTask();
                    mArpTableObservationTimer.scheduleAtFixedRate(task, 10, 1*1000);
                } else {
                    invokeSink2nd();
                }
            }
        });
    }

    private boolean isWifiDisplaySource(WifiP2pDevice dev) {
        try {
            if(dev == null)
                return false;

            Field refDeviceWfdInfo = dev.getClass().getDeclaredField("wfdInfo");
            refDeviceWfdInfo.setAccessible(true);
            Object refWfdInfo = refDeviceWfdInfo.get(dev);

            Method refIsWfdEnabled = refWfdInfo.getClass().getDeclaredMethod("isWfdEnabled", (Class[])null);
            Method refGetDeviceType = refWfdInfo.getClass().getDeclaredMethod("getDeviceType", (Class[])null);
            Method refGetControlPort = refWfdInfo.getClass().getDeclaredMethod("getControlPort", (Class[])null);

            if(!(boolean)refIsWfdEnabled.invoke(refWfdInfo, null))
                return false;

            int type = (int)refGetDeviceType.invoke(refWfdInfo, null);
            mP2pControlPort = (int)refGetControlPort.invoke(refWfdInfo, null);

            Field refWfdSource = dev.getClass().getDeclaredField("WFD_SOURCE");
            Field refSourceOrPrimarySink = dev.getClass().getDeclaredField("SOURCE_OR_PRIMARY_SINK");

            refWfdSource.setAccessible(true);
            refSourceOrPrimarySink.setAccessible(true);

            int WFD_SOURCE = (int)refWfdSource.get(refWfdInfo);
            int SOURCE_OR_PRIMARY_SINK = (int)refSourceOrPrimarySink.get(refWfdInfo);

            return (type == WFD_SOURCE) || (type == SOURCE_OR_PRIMARY_SINK);
        } catch (IllegalAccessException ex) {
            Log.e(TAG, ex.getMessage());
        } catch (NoSuchFieldException ex) {
            Log.e(TAG, ex.getMessage());
        } catch (NoSuchMethodException ex) {
            Log.e(TAG, ex.getMessage());
        } catch (InvocationTargetException ex) {
            Log.e(TAG, ex.getMessage());
        }
        return false;
    }

    private void invokeSink2nd() {
        manager.requestConnectionInfo(channel, new WifiP2pManager.ConnectionInfoListener() {
            public void onConnectionInfoAvailable(WifiP2pInfo info) {
                if (info == null) {
                    return;
                }

                if (!info.groupFormed) {
                    return;
                }

                if (info.isGroupOwner) {
                    return;
                } else {
                    String source_ip = info.groupOwnerAddress.getHostAddress();
                    if(mMiracastDeviceListener != null)
                        mMiracastDeviceListener.AddMiracastDevice(source_ip, mP2pControlPort);
                }
            }
        });
    }
}