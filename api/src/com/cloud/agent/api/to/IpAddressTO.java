/**
 *  Copyright (C) 2010 Cloud.com, Inc.  All rights reserved.
 * 
 * This software is licensed under the GNU General Public License v3 or later.
 * 
 * It is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 */

package com.cloud.agent.api.to;


public class IpAddressTO {
    
    private String publicIp;
    private boolean sourceNat;
    private boolean add;
    private boolean oneToOneNat;
    private boolean firstIP;
    private String vlanId;
    private String vlanGateway;
    private String vlanNetmask;
    private String vifMacAddress;
    private String guestIp;
    private Integer networkRate;
    
    
    public IpAddressTO(String ipAddress, boolean add, boolean firstIP, boolean sourceNat, String vlanId, String vlanGateway, String vlanNetmask, String vifMacAddress, String guestIp, Integer networkRate) {
        this.publicIp = ipAddress;
        this.add = add;
        this.firstIP = firstIP;
        this.sourceNat = sourceNat;
        this.vlanId = vlanId;
        this.vlanGateway = vlanGateway;
        this.vlanNetmask = vlanNetmask;
        this.vifMacAddress = vifMacAddress;
        this.guestIp = guestIp;
        this.networkRate = networkRate;
    }
    
    protected IpAddressTO() {
    }

    public String getGuestIp(){
        return guestIp;
    }

    public String getPublicIp() {
        return publicIp;
    }

    public boolean isAdd() {
        return add;
    }
    
    public boolean isOneToOneNat(){
        return this.oneToOneNat;
    }
    
    public boolean isFirstIP() {
        return firstIP;
    }

    public void setSourceNat(boolean sourceNat) {
        this.sourceNat = sourceNat;
    }

    public boolean isSourceNat() {
        return sourceNat;
    }
    
    public String getVlanId() {
        return vlanId;
    }
    
    public String getVlanGateway() {
        return vlanGateway;
    }
    
    public String getVlanNetmask() {
        return vlanNetmask;
    }
    
    public String getVifMacAddress() {
        return vifMacAddress;
    }
    
    public Integer getNetworkRate() {
        return networkRate;
    }

}