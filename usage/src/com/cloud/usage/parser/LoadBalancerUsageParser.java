/**
 *  Copyright (C) 2011 Citrix Systems, Inc.  All rights reserved
 *
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
 */

package com.cloud.usage.parser;

import java.text.DecimalFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import com.cloud.usage.UsageLoadBalancerPolicyVO;
import com.cloud.usage.UsageServer;
import com.cloud.usage.UsageTypes;
import com.cloud.usage.UsageVO;
import com.cloud.usage.dao.UsageDao;
import com.cloud.usage.dao.UsageLoadBalancerPolicyDao;
import com.cloud.user.AccountVO;
import com.cloud.utils.Pair;
import com.cloud.utils.component.ComponentLocator;

public class LoadBalancerUsageParser {
	public static final Logger s_logger = Logger.getLogger(LoadBalancerUsageParser.class.getName());
	
	private static ComponentLocator _locator = ComponentLocator.getLocator(UsageServer.Name, "usage-components.xml", "log4j-cloud_usage");
	private static UsageDao m_usageDao = _locator.getDao(UsageDao.class);
	private static UsageLoadBalancerPolicyDao m_usageLoadBalancerPolicyDao = _locator.getDao(UsageLoadBalancerPolicyDao.class);
	
	public static boolean parse(AccountVO account, Date startDate, Date endDate) {
	    if (s_logger.isDebugEnabled()) {
	        s_logger.debug("Parsing all LoadBalancerPolicy usage events for account: " + account.getId());
	    }
		if ((endDate == null) || endDate.after(new Date())) {
			endDate = new Date();
		}

        // - query usage_volume table with the following criteria:
        //     - look for an entry for accountId with start date in the given range
        //     - look for an entry for accountId with end date in the given range
        //     - look for an entry for accountId with end date null (currently running vm or owned IP)
        //     - look for an entry for accountId with start date before given range *and* end date after given range
        List<UsageLoadBalancerPolicyVO> usageLBs = m_usageLoadBalancerPolicyDao.getUsageRecords(account.getId(), account.getDomainId(), startDate, endDate, false, 0);
        
        if(usageLBs.isEmpty()){
        	s_logger.debug("No load balancer usage events for this period");
        	return true;
        }

        // This map has both the running time *and* the usage amount.
        Map<String, Pair<Long, Long>> usageMap = new HashMap<String, Pair<Long, Long>>();
        Map<String, LBInfo> lbMap = new HashMap<String, LBInfo>();

		// loop through all the load balancer policies, create a usage record for each
        for (UsageLoadBalancerPolicyVO usageLB : usageLBs) {
            long lbId = usageLB.getId();
            String key = ""+lbId;
            
            lbMap.put(key, new LBInfo(lbId, usageLB.getZoneId()));
            
            Date lbCreateDate = usageLB.getCreated();
            Date lbDeleteDate = usageLB.getDeleted();

            if ((lbDeleteDate == null) || lbDeleteDate.after(endDate)) {
            	lbDeleteDate = endDate;
            }

            // clip the start date to the beginning of our aggregation range if the vm has been running for a while
            if (lbCreateDate.before(startDate)) {
            	lbCreateDate = startDate;
            }

            long currentDuration = (lbDeleteDate.getTime() - lbCreateDate.getTime()) + 1; // make sure this is an inclusive check for milliseconds (i.e. use n - m + 1 to find total number of millis to charge)


            updateLBUsageData(usageMap, key, usageLB.getId(), currentDuration);
        }

        for (String lbIdKey : usageMap.keySet()) {
            Pair<Long, Long> sgtimeInfo = usageMap.get(lbIdKey);
            long useTime = sgtimeInfo.second().longValue();

            // Only create a usage record if we have a runningTime of bigger than zero.
            if (useTime > 0L) {
            	LBInfo info = lbMap.get(lbIdKey);
                createUsageRecord(UsageTypes.LOAD_BALANCER_POLICY, useTime, startDate, endDate, account, info.getId(), info.getZoneId() );
            }
        }

        return true;
	}

	private static void updateLBUsageData(Map<String, Pair<Long, Long>> usageDataMap, String key, long lbId, long duration) {
        Pair<Long, Long> lbUsageInfo = usageDataMap.get(key);
        if (lbUsageInfo == null) {
        	lbUsageInfo = new Pair<Long, Long>(new Long(lbId), new Long(duration));
        } else {
            Long runningTime = lbUsageInfo.second();
            runningTime = new Long(runningTime.longValue() + duration);
            lbUsageInfo = new Pair<Long, Long>(lbUsageInfo.first(), runningTime);
        }
        usageDataMap.put(key, lbUsageInfo);
	}

	private static void createUsageRecord(int type, long runningTime, Date startDate, Date endDate, AccountVO account, long lbId, long zoneId) {
        // Our smallest increment is hourly for now
        if (s_logger.isDebugEnabled()) {
            s_logger.debug("Total running time " + runningTime + "ms");
        }

        float usage = runningTime / 1000f / 60f / 60f;

        DecimalFormat dFormat = new DecimalFormat("#.######");
        String usageDisplay = dFormat.format(usage);

        if (s_logger.isDebugEnabled()) {
            s_logger.debug("Creating Volume usage record for load balancer: " + lbId + ", usage: " + usageDisplay + ", startDate: " + startDate + ", endDate: " + endDate + ", for account: " + account.getId());
        }

        // Create the usage record
        String usageDesc = "Load Balancing Policy: "+lbId+" usage time";

        //ToDo: get zone id
        UsageVO usageRecord = new UsageVO(zoneId, account.getId(), account.getDomainId(), usageDesc, usageDisplay + " Hrs", type,
                new Double(usage), null, null, null, null, lbId, null, startDate, endDate);
        m_usageDao.persist(usageRecord);
    }
	
	private static class LBInfo {
	    private long id;
	    private long zoneId;

	    public LBInfo(long id, long zoneId) {
	        this.id = id;
	        this.zoneId = zoneId;
	    }
	    public long getZoneId() {
	        return zoneId;
	    }
	    public long getId() {
	        return id;
	    }
	}

}