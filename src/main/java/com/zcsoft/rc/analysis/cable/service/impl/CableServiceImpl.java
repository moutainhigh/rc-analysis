package com.zcsoft.rc.analysis.cable.service.impl;

import com.sharingif.cube.core.util.StringUtils;
import com.zcsoft.rc.analysis.cable.dao.CableDAO;
import com.zcsoft.rc.analysis.cable.dao.CablePolygonDAO;
import com.zcsoft.rc.analysis.cable.dao.SafetyZoneDAO;
import com.zcsoft.rc.analysis.cable.model.entity.CableBuild;
import com.zcsoft.rc.analysis.cable.service.CableService;
import com.zcsoft.rc.analysis.machinery.service.MachineryService;
import com.zcsoft.rc.analysis.sys.service.SysParameterService;
import com.zcsoft.rc.analysis.warning.service.WorkWarningService;
import com.zcsoft.rc.collectors.api.rc.entity.CurrentRcRsp;
import com.zcsoft.rc.machinery.model.entity.Machinery;
import com.zcsoft.rc.warning.model.entity.WorkWarning;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CableServiceImpl implements CableService {

    private Map<String, CableBuild> cableBuildMap = new ConcurrentHashMap<>(200);

    private CableDAO cableDAO;
    private CablePolygonDAO cablePolygonDAO;
    private SafetyZoneDAO safetyZoneDAO;

    private SysParameterService sysParameterService;
    private MachineryService machineryService;
    private WorkWarningService workWarningService;

    @Resource
    public void setCableDAO(CableDAO cableDAO) {
        this.cableDAO = cableDAO;
    }
    @Resource
    public void setCablePolygonDAO(CablePolygonDAO cablePolygonDAO) {
        this.cablePolygonDAO = cablePolygonDAO;
    }
    @Resource
    public void setSafetyZoneDAO(SafetyZoneDAO safetyZoneDAO) {
        this.safetyZoneDAO = safetyZoneDAO;
    }
    @Resource
    public void setSysParameterService(SysParameterService sysParameterService) {
        this.sysParameterService = sysParameterService;
    }
    @Resource
    public void setMachineryService(MachineryService machineryService) {
        this.machineryService = machineryService;
    }
    @Resource
    public void setWorkWarningService(WorkWarningService workWarningService) {
        this.workWarningService = workWarningService;
    }

    /**
     * 线缆附近动土
     * @param workRadius
     * @param currentRcRsp
     * @return
     */
    protected boolean analysisMovingSoilNearCables(double workRadius, CurrentRcRsp currentRcRsp) {
        // 线缆附近动土报警距离
        int cableLimitDistance = sysParameterService.getCableLimitDistance();
        int cableLimitTime = sysParameterService.getCableLimitTime();
        double maxDistance = cableLimitDistance+workRadius;
        if(maxDistance<0) {
            maxDistance = 0;
        }

        CableBuild cableBuild = cableBuildMap.get(currentRcRsp.getId());
        if(cableBuild == null) {
            cableBuild = new CableBuild(currentRcRsp.getId(), currentRcRsp.getLongitude(), currentRcRsp.getLatitude(), cableLimitTime);

            cableBuildMap.put(currentRcRsp.getId(), cableBuild);
            return false;
        } else {
            cableBuild.addCoordinateDate(currentRcRsp.getLongitude(), currentRcRsp.getLatitude());
        }

        String nearDataId;
        if(!cableBuild.isNotMoved()) {
            nearDataId = cableDAO.near("geometry",currentRcRsp.getLongitude(),currentRcRsp.getLatitude(),maxDistance,0);
            if(StringUtils.isTrimEmpty(nearDataId)) {
                return false;
            }
            return true;
        }

        nearDataId = cableDAO.near("geometry",currentRcRsp.getLongitude(),currentRcRsp.getLatitude(),maxDistance,0);
        if(StringUtils.isTrimEmpty(nearDataId)) {
            return false;
        } else {
            workWarningService.addCableWarning(currentRcRsp.getId(), WorkWarning.TYPE_MOVING_SOIL_NEAR_CABLES, currentRcRsp.getLongitude(), currentRcRsp.getLatitude());
            return true;
        }
    }

    /**
     * 处理安全区域
     */
    protected boolean safetyZone(CurrentRcRsp currentRcRsp) {
        String nearDataId = safetyZoneDAO.intersects("geometry", currentRcRsp.getLongitude(), currentRcRsp.getLatitude());

        if(StringUtils.isTrimEmpty(nearDataId)) {
            return false;
        } else {
            return true;
        }
    }

    @Override
    public void analysis(CurrentRcRsp currentRcRsp) {

        Machinery machinery = machineryService.getMachinery(currentRcRsp.getId());

        if(machinery == null) {
            return;
        }

        String typeRollingAlarmMachinery = sysParameterService.getTypeRollingAlarmMachinery();

        if(!typeRollingAlarmMachinery.equals(machinery.getMachineryType())) {
            return;
        }

        // 如果在安全区域内消除警告并返回
        if(safetyZone(currentRcRsp)) {
            workWarningService.finishCableWarning(currentRcRsp.getId());
            return;
        }

        double workRadius = machinery.getWorkRadius()/100d;

        // 碾压限定距离
        int rollingLimitDistance = sysParameterService.getRollingLimitDistance();

        double maxDistance = rollingLimitDistance+workRadius;

        if(maxDistance<0) {
            maxDistance = 0;
        }

        String nearDataId = cableDAO.near("geometry",currentRcRsp.getLongitude(),currentRcRsp.getLatitude(),maxDistance,0);
        if(StringUtils.isTrimEmpty(nearDataId)) {
            nearDataId = cablePolygonDAO.intersects("geometry", currentRcRsp.getLongitude(), currentRcRsp.getLatitude());
        }

        if(StringUtils.isTrimEmpty(nearDataId)) {
            boolean flag = analysisMovingSoilNearCables(workRadius, currentRcRsp);

            if(!flag) {
                workWarningService.finishCableWarning(currentRcRsp.getId());
            }
        } else {
            workWarningService.addCableWarning(currentRcRsp.getId(), WorkWarning.TYPE_ROLLING_CABLE, currentRcRsp.getLongitude(), currentRcRsp.getLatitude());
            return;
        }


    }

}
