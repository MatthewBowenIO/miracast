package com.squirrels.miracast;

/**
 * Created by matthewbowen on 12/1/16.
 */

public interface MiracastDeviceListener {
    /**
     * Endpoint used to notify the client of a Miracast connection request.
     *
     * @param ipAddress: IP Address of the Miracast Device that wants to connect.
     * @param port: Port of the Miracast Device that wants to connect.
     */
    void AddMiracastDevice(String ipAddress, int port);

    /**
     * Endpoint used to notify the client of a Miracast disconnection.
     *
     * @param ipAddress: IP Address of the Miracast Device that is disconnecting.
     */
    void RemoveMiracastDevice(String ipAddress);
}
