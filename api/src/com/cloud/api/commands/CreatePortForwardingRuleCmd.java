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

package com.cloud.api.commands;

import java.util.List;

import org.apache.log4j.Logger;

import com.cloud.api.ApiConstants;
import com.cloud.api.BaseAsyncCmd;
import com.cloud.api.BaseAsyncCreateCmd;
import com.cloud.api.BaseCmd;
import com.cloud.api.Implementation;
import com.cloud.api.Parameter;
import com.cloud.api.ServerApiException;
import com.cloud.api.BaseCmd.CommandType;
import com.cloud.api.response.FirewallRuleResponse;
import com.cloud.event.EventTypes;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.exception.NetworkRuleConflictException;
import com.cloud.exception.ResourceUnavailableException;
import com.cloud.network.IpAddress;
import com.cloud.network.rules.PortForwardingRule;
import com.cloud.user.Account;
import com.cloud.user.UserContext;
import com.cloud.utils.net.Ip;
import com.cloud.utils.net.NetUtils;

@Implementation(description = "Creates a port forwarding rule", responseObject = FirewallRuleResponse.class)
public class CreatePortForwardingRuleCmd extends BaseAsyncCreateCmd implements PortForwardingRule {
    public static final Logger s_logger = Logger.getLogger(CreatePortForwardingRuleCmd.class.getName());

    private static final String s_name = "createportforwardingruleresponse";

    // ///////////////////////////////////////////////////
    // ////////////// API parameters /////////////////////
    // ///////////////////////////////////////////////////

    @Parameter(name = ApiConstants.IP_ADDRESS_ID, type = CommandType.LONG, required = true, description = "the IP address id of the port forwarding rule")
    private Long ipAddressId;

    @Parameter(name = ApiConstants.PRIVATE_START_PORT, type = CommandType.INTEGER, required = true, description = "the starting port of port forwarding rule's private port range")
    private Integer privateStartPort;

    @Parameter(name = ApiConstants.PRIVATE_END_PORT, type = CommandType.INTEGER, required = false, description = "the ending port of port forwarding rule's private port range")
    private Integer privateEndPort;

    @Parameter(name = ApiConstants.PROTOCOL, type = CommandType.STRING, required = true, description = "the protocol for the port fowarding rule. Valid values are TCP or UDP.")
    private String protocol;

    @Parameter(name = ApiConstants.PUBLIC_START_PORT, type = CommandType.INTEGER, required = true, description = "the starting port of port forwarding rule's public port range")
    private Integer publicStartPort;

    @Parameter(name = ApiConstants.PUBLIC_END_PORT, type = CommandType.INTEGER, required = false, description = "the ending port of port forwarding rule's private port range")
    private Integer publicEndPort;

    @Parameter(name = ApiConstants.VIRTUAL_MACHINE_ID, type = CommandType.LONG, required = true, description = "the ID of the virtual machine for the port forwarding rule")
    private Long virtualMachineId;
    
    @Parameter(name = ApiConstants.CIDR_LIST, type = CommandType.LIST, collectionType = CommandType.STRING, description = "the cidr list to forward traffic from")
    private List<String> cidrlist;

    
    // ///////////////////////////////////////////////////
    // ///////////////// Accessors ///////////////////////
    // ///////////////////////////////////////////////////

    public Long getIpAddressId() {
        return ipAddressId;
    }

    @Override
    public String getProtocol() {
        return protocol.trim();
    }

    @Override
    public long getVirtualMachineId() {
        return virtualMachineId;
    }

    public List<String> getSourceCidrList() {
        return cidrlist;
    }

    // ///////////////////////////////////////////////////
    // ///////////// API Implementation///////////////////
    // ///////////////////////////////////////////////////

    @Override
    public String getCommandName() {
        return s_name;
    }
    
    @Override
    public void setSourceCidrList(List<String> cidrs){
        cidrlist = cidrs;
    }

    @Override
    public void execute() throws ResourceUnavailableException {
        UserContext callerContext = UserContext.current();
        boolean success = false;
        PortForwardingRule rule = _entityMgr.findById(PortForwardingRule.class, getEntityId());
        try {
            UserContext.current().setEventDetails("Rule Id: " + getEntityId());
            success = _rulesService.applyPortForwardingRules(rule.getSourceIpAddressId(), callerContext.getCaller());

            // State is different after the rule is applied, so get new object here
            rule = _entityMgr.findById(PortForwardingRule.class, getEntityId());
            FirewallRuleResponse fwResponse = new FirewallRuleResponse(); 
            if (rule != null) {
                fwResponse = _responseGenerator.createFirewallRuleResponse(rule);
                setResponseObject(fwResponse);
            }
            fwResponse.setResponseName(getCommandName());
        } finally {
            if (!success || rule == null) {
                _rulesService.revokePortForwardingRule(getEntityId(), true);
                throw new ServerApiException(BaseCmd.INTERNAL_ERROR, "Failed to apply port forwarding rule");
            }
        }
    }

    @Override
    public long getId() {
        throw new UnsupportedOperationException("database id can only provided by VO objects");
    }

    @Override
    public String getXid() {
        // FIXME: We should allow for end user to specify Xid.
        return null;
    }

    @Override
    public long getSourceIpAddressId() {
        return ipAddressId;
    }

    @Override
    public int getSourcePortStart() {
        return publicStartPort.intValue();
    }

    @Override
    public int getSourcePortEnd() {
        return (publicEndPort == null)? publicStartPort.intValue() : publicEndPort.intValue();        
    }

    @Override
    public Purpose getPurpose() {
        return Purpose.PortForwarding;
    }

    @Override
    public State getState() {
        throw new UnsupportedOperationException("Should never call me to find the state");
    }

    @Override
    public long getNetworkId() {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public long getEntityOwnerId() {
        Account account = UserContext.current().getCaller();

        if (account != null) {
            return account.getId();
        }

        return Account.ACCOUNT_ID_SYSTEM; // no account info given, parent this command to SYSTEM so ERROR events are tracked
    }

    @Override
    public long getDomainId() {
        IpAddress ip = _networkService.getIp(ipAddressId);
        return ip.getDomainId();
    }

    @Override
    public Ip getDestinationIpAddress() {
        return null;
    }

    @Override
    public int getDestinationPortStart() {
        return privateStartPort.intValue();
    }

    @Override
    public int getDestinationPortEnd() {
        return (privateEndPort == null)? privateStartPort.intValue() : privateEndPort.intValue();
    }

    @Override
    public void create() {
        if (cidrlist != null)
            for (String cidr: cidrlist){
                if (!NetUtils.isValidCIDR(cidr)){
                    throw new ServerApiException(BaseCmd.PARAM_ERROR, "Source cidrs formatting error " + cidr); 
                }
            }
        try {
            PortForwardingRule result = _rulesService.createPortForwardingRule(this, virtualMachineId);
            setEntityId(result.getId());
        } catch (NetworkRuleConflictException ex) {
            s_logger.info("Network rule conflict: " + ex.getMessage());
            s_logger.trace("Network Rule Conflict: ", ex);
            throw new ServerApiException(BaseCmd.NETWORK_RULE_CONFLICT_ERROR, ex.getMessage());
        }
    }

    @Override
    public String getEventType() {
        return EventTypes.EVENT_NET_RULE_ADD;
    }

    @Override
    public String getEventDescription() {
        IpAddress ip = _networkService.getIp(ipAddressId);
        return ("Applying port forwarding  rule for Ip: " + ip.getAddress() + " with virtual machine:" + virtualMachineId);
    }

    @Override
    public long getAccountId() {
        IpAddress ip = _networkService.getIp(ipAddressId);
        return ip.getAccountId();
    }

    @Override
    public String getSyncObjType() {
        return BaseAsyncCmd.networkSyncObject;
    }

    @Override
    public Long getSyncObjId() {
        return getIp().getAssociatedWithNetworkId();
    }

    private IpAddress getIp() {
        IpAddress ip = _networkService.getIp(ipAddressId);
        if (ip == null) {
            throw new InvalidParameterValueException("Unable to find ip address by id " + ipAddressId);
        }
        return ip;
    }

}