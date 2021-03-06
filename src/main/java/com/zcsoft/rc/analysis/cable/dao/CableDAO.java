package com.zcsoft.rc.analysis.cable.dao;

import com.zcsoft.rc.analysis.app.dao.ICubeMongoDBDAO;

public interface CableDAO extends ICubeMongoDBDAO {

    /**
     * 根据fieldName、经度、纬度、距离查询
     * @param fieldName
     * @param longitude
     * @param latitude
     * @param maxDistance
     * @param minDistance
     * @return
     */
    String near(String fieldName, double longitude, double latitude, double maxDistance, double minDistance);

}
