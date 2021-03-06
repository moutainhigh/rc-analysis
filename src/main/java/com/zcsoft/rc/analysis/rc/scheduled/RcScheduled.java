package com.zcsoft.rc.analysis.rc.scheduled;

import com.sharingif.cube.core.util.StringUtils;
import com.zcsoft.rc.analysis.cable.service.CableService;
import com.zcsoft.rc.analysis.cordon.service.CordonService;
import com.zcsoft.rc.analysis.railway.service.RailwayLinesService;
import com.zcsoft.rc.analysis.warning.service.WarningService;
import com.zcsoft.rc.collectors.api.rc.entity.CurrentRcMapRsp;
import com.zcsoft.rc.collectors.api.rc.entity.CurrentRcRsp;
import com.zcsoft.rc.collectors.api.rc.service.CurrentRcApiService;
import com.zcsoft.rc.user.model.entity.User;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Map;

@Component
@EnableScheduling
public class RcScheduled {

    private ThreadPoolTaskExecutor workThreadPoolTaskExecutor;
    private CurrentRcApiService currentRcApiService;
    private CordonService cordonService;
    private CableService cableService;
    private WarningService warningService;
    private RailwayLinesService railwayLinesService;

    @Resource
    public void setWorkThreadPoolTaskExecutor(ThreadPoolTaskExecutor workThreadPoolTaskExecutor) {
        this.workThreadPoolTaskExecutor = workThreadPoolTaskExecutor;
    }
    @Resource
    public void setCurrentRcApiService(CurrentRcApiService currentRcApiService) {
        this.currentRcApiService = currentRcApiService;
    }
    @Resource
    public void setCordonService(CordonService cordonService) {
        this.cordonService = cordonService;
    }
    @Resource
    public void setCableService(CableService cableService) {
        this.cableService = cableService;
    }
    @Resource
    public void setWarningService(WarningService warningService) {
        this.warningService = warningService;
    }
    @Resource
    public void setRailwayLinesService(RailwayLinesService railwayLinesService) {
        this.railwayLinesService = railwayLinesService;
    }

    @Scheduled(fixedRate = 1000*1)
    public synchronized void analysis() {
        if(!warningService.isOpen()) {
            return;
        }

        CurrentRcMapRsp currentRcMapRsp = currentRcApiService.all();

        Map<String,CurrentRcRsp> rcMap = currentRcMapRsp.getRcMap();

        if(rcMap == null || rcMap.isEmpty()) {
            return;
        }

        rcMap.forEach((id, currentRcRsp) -> {
            if(!StringUtils.isTrimEmpty(currentRcRsp.getWristStrapCode())){
                workThreadPoolTaskExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        cordonService.analysis(currentRcRsp);
                    }
                });
            }

            if(User.BUILDER_USER_TYPE_LOCOMOTIVE.equals(currentRcRsp.getType())) {
                cableService.analysis(currentRcRsp);
            }

            workThreadPoolTaskExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    railwayLinesService.analysis(currentRcRsp);
                }
            });
        });

    }

}
