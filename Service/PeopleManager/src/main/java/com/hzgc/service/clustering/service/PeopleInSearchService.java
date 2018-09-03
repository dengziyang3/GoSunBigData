package com.hzgc.service.clustering.service;

import com.hzgc.common.collect.bean.FaceObject;
import com.hzgc.common.faceclustering.PeopleInAttribute;
import com.hzgc.common.faceclustering.table.ClusteringTable;
import com.hzgc.common.service.api.bean.DeviceDTO;
import com.hzgc.common.service.api.bean.WebgisMapPointDTO;
import com.hzgc.common.service.api.service.DeviceQueryService;
import com.hzgc.service.clustering.bean.export.Locus;
import com.hzgc.service.clustering.bean.export.PeopleInHistoryRecord;
import com.hzgc.service.clustering.bean.export.PeopleInResult;
import com.hzgc.service.clustering.bean.param.SortParam;
import com.hzgc.service.clustering.dao.HBaseDao;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * 迁入人口结果查询接口实现
 */
@Service
@Slf4j
public class PeopleInSearchService {
    @Autowired
    private HBaseDao hBaseDao;

    @Autowired
    private DeviceQueryService deviceQueryService;

    @Autowired
    private ClusteringServiceHelper clusteringServiceHelper;


    /**
     * 查询聚类信息
     *
     * @param time 聚类时间月份
     * @param start 返回数据下标开始符号
     * @param limit 行数
     * @param sortParam 排序参数（默认按出现次数排序）
     * @return 聚类列表
     */
    public PeopleInResult searchAllClustering(String region, String time, int start, int limit, String sortParam) {
        //查询不忽略的对象
        List<PeopleInAttribute> listNotIgnore = hBaseDao.getClustering(region, time, ClusteringTable.ClUSTERINGINFO_COLUMN_YES);
        //查询忽略的对象
        List<PeopleInAttribute> listIgnore = hBaseDao.getClustering(region, time, ClusteringTable.ClUSTERINGINFO_COLUMN_NO);
        if (!StringUtils.isBlank(sortParam)) {
            SortParam sortParams = ListUtils.getOrderStringBySort(sortParam);
            ListUtils.sort(listNotIgnore, sortParams.getSortNameArr(), sortParams.getIsAscArr());
            ListUtils.sort(listIgnore, sortParams.getSortNameArr(), sortParams.getIsAscArr());
        }
        int totalYes = listNotIgnore==null? 0:listNotIgnore.size();
        int totalNo = listIgnore==null? 0:listIgnore.size();
        int total = totalYes + totalNo;
        PeopleInResult peopleInResult = new PeopleInResult();
        //优先返回不忽略的聚类
        log.info("totalYes="+totalYes+",totalNo="+totalNo+",total="+total);
        if(start < total && total > 0) {
            if(totalYes > 0) {
                if((start + limit) <= totalYes) {
                    peopleInResult.setPeopleInAttributeList(listNotIgnore.subList(start, start + limit));
                    log.info("[PeopleInSearchService] searchAllClustering attributes not ignored index range : [" + start + "," + (start + limit) + ")");
                } else if((start + limit) > totalYes && start<=totalYes) {
                    peopleInResult.setPeopleInAttributeList(listNotIgnore.subList(start, totalYes));
                    log.info("[PeopleInSearchService] searchAllClustering attributes not ignored index range : [" + start + "," + (totalYes) + ")");
                    if((start + limit) <= total) {
                        peopleInResult.setPeopleInAttributeList_ignore(listIgnore.subList(0, start + limit - totalYes));
                        log.info("[PeopleInSearchService] searchAllClustering attributes ignored index range : [" + 0 +"," + (start + limit - totalYes) + ")");
                    } else if(listIgnore != null){
                        peopleInResult.setPeopleInAttributeList_ignore(listIgnore.subList(0, totalNo));
                        log.info("[PeopleInSearchService] searchAllClustering attributes ignored index range : [" + start + "," + (totalNo) + ")");
                    } else {
                        log.info("[PeopleInSearchService] searchAllClustering listIgnore is null");
                    }
                } else if (start > totalYes && (start + limit - totalYes) <= totalNo) {
                    peopleInResult.setPeopleInAttributeList_ignore(listIgnore.subList(start + limit - totalYes, start + limit - totalYes + limit));
                    log.info("[PeopleInSearchService] searchAllClustering attributes ignored index range : [" + (start + limit - totalYes) + "," + (start + limit - totalYes + limit) + ")");
                } else if (listIgnore != null){
                    peopleInResult.setPeopleInAttributeList_ignore(listIgnore.subList(start - totalYes, totalNo));
                    log.info("[PeopleInSearchService] searchAllClustering attributes ignored index range : [" + (start - totalYes) + "," + (totalNo) + ")");
                } else {
                    log.info("[PeopleInSearchService] searchAllClustering listIgnore is null");
                }
            } else {
                if((start + limit) <= totalNo) {
                    peopleInResult.setPeopleInAttributeList_ignore(listIgnore.subList(start, start + limit));
                    log.info("[PeopleInSearchService] searchAllClustering attributes ignored index range : [" + start + "," + (start + limit) + ")");
                } else if(listIgnore != null){
                    peopleInResult.setPeopleInAttributeList_ignore(listIgnore.subList(start, totalNo));
                    log.info("[PeopleInSearchService] searchAllClustering attributes ignored index range : [" + start + "," + (totalNo) + ")");
                } else {
                    log.info("[PeopleInSearchService] searchAllClustering listIgnore is null");
                }
            }
        } else {
            log.info("[PeopleInSearchService] searchAllClustering start out of index or not data");
        }
        peopleInResult.setTotalCount(total);
        //ipcId转ipcName
        HashSet<String> ipcIdSet = new HashSet<>();
        List<PeopleInAttribute> peopleInAttributeList = new ArrayList<>();
        List<PeopleInAttribute> peopleInAttributeList_ignore = new ArrayList<>();
        if(peopleInResult.getPeopleInAttributeList() != null) {
            for (PeopleInAttribute attribute : peopleInResult.getPeopleInAttributeList()) {
                ipcIdSet.add(attribute.getFirstIpcId());
                ipcIdSet.add(attribute.getLastIpcId());
            }
        }
        if(peopleInResult.getPeopleInAttributeList_ignore() != null) {
            for (PeopleInAttribute attribute : peopleInResult.getPeopleInAttributeList_ignore()) {
                ipcIdSet.add(attribute.getFirstIpcId());
                ipcIdSet.add(attribute.getLastIpcId());
            }
        }
        ArrayList<String> ipcIds = new ArrayList<>(ipcIdSet);

        log.info("==========================ipcIds="+ipcIds);
        Map<String, DeviceDTO> deviceInfo = deviceQueryService.getDeviceInfoByBatchIpc(ipcIds);
        if(peopleInResult.getPeopleInAttributeList() != null && deviceInfo != null) {
            for (PeopleInAttribute attribute : peopleInResult.getPeopleInAttributeList()) {
                if(deviceInfo.get(attribute.getFirstIpcId()) != null) {
                    attribute.setFirstIpcId(deviceInfo.get(attribute.getFirstIpcId()).getName());
                }
                if(deviceInfo.get(attribute.getLastIpcId()) != null) {
                    attribute.setLastIpcId(deviceInfo.get(attribute.getLastIpcId()).getName());
                }
                attribute.setFirstAppearTime(attribute.getFirstAppearTime().replaceAll("(\\d{4})(\\d{2})(\\d{2})(\\d{2})(\\d{2})(\\d{2})","$1-$2-$3 $4:$5:$6"));
                attribute.setLastAppearTime(attribute.getLastAppearTime().replaceAll("(\\d{4})(\\d{2})(\\d{2})(\\d{2})(\\d{2})(\\d{2})","$1-$2-$3 $4:$5:$6"));
                attribute.setFtpUrl(clusteringServiceHelper.getFtpUrl(attribute.getFtpUrl()));
                peopleInAttributeList.add(attribute);
            }
            peopleInResult.setPeopleInAttributeList(peopleInAttributeList);
        }
        if(peopleInResult.getPeopleInAttributeList_ignore() != null && deviceInfo != null) {
            for (PeopleInAttribute attribute : peopleInResult.getPeopleInAttributeList_ignore()) {
                if(deviceInfo.get(attribute.getFirstIpcId()) != null) {
                    attribute.setFirstIpcId(deviceInfo.get(attribute.getFirstIpcId()).getName());
                }
                if(deviceInfo.get(attribute.getLastIpcId()) != null) {
                    attribute.setLastIpcId(deviceInfo.get(attribute.getLastIpcId()).getName());
                }
                attribute.setLastAppearTime(attribute.getLastAppearTime().replaceAll("(\\d{4})(\\d{2})(\\d{2})(\\d{2})(\\d{2})(\\d{2})","$1-$2-$3 $4:$5:$6"));
                attribute.setFirstAppearTime(attribute.getFirstAppearTime().replaceAll("(\\d{4})(\\d{2})(\\d{2})(\\d{2})(\\d{2})(\\d{2})","$1-$2-$3 $4:$5:$6"));
                attribute.setFtpUrl(clusteringServiceHelper.getFtpUrl(attribute.getFtpUrl()));
                peopleInAttributeList_ignore.add(attribute);
            }
            peopleInResult.setPeopleInAttributeList_ignore(peopleInAttributeList_ignore);
        }
        return peopleInResult;
    }


    /**
     * 查询单个聚类详细信息(告警ID)
     *
     * @param rowKeys   告警系列rowKey
     * @param start     分页查询开始行
     * @param limit     查询条数
     * @return 返回该类下面所以告警信息
     */
    public List<PeopleInHistoryRecord> historyRecordSearch(List<String> rowKeys, int start, int limit) {

        HashSet<String> ipcIdSet = new HashSet<>();
        List<PeopleInHistoryRecord> peopleInHistoryRecordList = new ArrayList<>();
        if(rowKeys != null) {
            for (String rowKey : rowKeys) {
                ipcIdSet.add(rowKey.split("_")[0]);
            }
            ArrayList<String> ipcIds = new ArrayList<>(ipcIdSet);
            log.info("[PeopleInSearchService] historyRecordSearch ipcIds="+ipcIds);
            Map<String, DeviceDTO> deviceInfo = deviceQueryService.getDeviceInfoByBatchIpc(ipcIds);
            List<FaceObject> faceObjects;
            if((start + limit) <= rowKeys.size()) {
                faceObjects = hBaseDao.getAlarmInfo(rowKeys.subList(start, start + limit));
            } else {
                faceObjects = hBaseDao.getAlarmInfo(rowKeys.subList(start, rowKeys.size()));
            }
            if(faceObjects != null) {
                for (FaceObject faceObject : faceObjects) {
                    PeopleInHistoryRecord record = new PeopleInHistoryRecord();
                    if (deviceInfo != null && deviceInfo.get(faceObject.getIpcId()) != null) {
                        String ipcName = deviceInfo.get(faceObject.getIpcId()).getName();
                        record.setIpcName(ipcName);
                    } else {
                        record.setIpcName(faceObject.getIpcId());
                    }
                    record.setRecordTime(faceObject.getTimeStamp());
                    record.setSurl(clusteringServiceHelper.getFtpUrl(faceObject.getSurl()));
                    record.setBurl(clusteringServiceHelper.getFtpUrl(faceObject.getBurl()));
                    peopleInHistoryRecordList.add(record);
                }
            } else {
                log.info("[PeopleInSearchService] historyRecordSearch faceObjects is null");
            }
        } else {
            log.info("[PeopleInSearchService] historyRecordSearch rowKeys is null");
        }
        return peopleInHistoryRecordList;
    }

    /**
     * 查询单个聚类
     *
     * @param time 时间月份
     * @param region 区域ID
     * @param clusterId 聚类ID
     * @param flag 忽略聚类标志
     * @return 单个聚类信息
     */
    public PeopleInAttribute searchClustering(String time, String region, String clusterId, String flag) {
        byte[] colName;
        if (flag.toLowerCase().equals("yes")) {
            colName = ClusteringTable.ClUSTERINGINFO_COLUMN_NO;
        } else if (flag.toLowerCase().equals("no")) {
            colName = ClusteringTable.ClUSTERINGINFO_COLUMN_YES;
        } else {
            log.info("[PeopleInSearchService] searchClustering Param flag is error, it must be yes or no");
            return null;
        }
        List<PeopleInAttribute> peopleInAttributeList = hBaseDao.getClustering(region, time, colName);
        if(peopleInAttributeList != null) {
            for(PeopleInAttribute attribute : peopleInAttributeList) {
                if(clusterId.equals(attribute.getClusteringId())) {
                    return attribute;
                }
            }
        }
        return null;

    }

    /**
     * 删除单个聚类
     *
     * @param time 时间月份
     * @param region 区域ID
     * @param clusterId 聚类ID
     * @param flag 忽略聚类标志
     * @return 成功 & 失败
     */
    public boolean deleteClustering(String time, String region, String clusterId, String flag) {

        byte[] colName;
        if (flag.toLowerCase().equals("yes")) {
            colName = ClusteringTable.ClUSTERINGINFO_COLUMN_NO;
        } else if (flag.toLowerCase().equals("no")) {
            colName = ClusteringTable.ClUSTERINGINFO_COLUMN_YES;
        } else {
            log.info("[PeopleInSearchService] deleteClustering Param flag is error, it must be yes or no");
            return false;
        }
        List<PeopleInAttribute> peopleInAttributeList = hBaseDao.getClustering(region, time, colName);
        Iterator<PeopleInAttribute> iterator = peopleInAttributeList.iterator();
        PeopleInAttribute peopleInAttribute;
        while (iterator.hasNext()) {
            peopleInAttribute = iterator.next();
            if (clusterId.equals(peopleInAttribute.getClusteringId())) {
                iterator.remove();
            }
        }
        return hBaseDao.putClustering(region, time, colName, peopleInAttributeList);

    }

    /**
     * 忽略单个聚类
     *
     * @param time 时间月份
     * @param region 区域ID
     * @param clusterId 聚类ID
     * @param flag 忽略聚类标志
     * @return 成功 & 失败
     */
    public boolean ignoreClustering(String time, String region, String clusterId, String flag) {

        byte[] colNameSrc;
        byte[] colNameDes;
        if (flag.toLowerCase().equals("yes")) {
            colNameSrc = ClusteringTable.ClUSTERINGINFO_COLUMN_YES;
            colNameDes = ClusteringTable.ClUSTERINGINFO_COLUMN_NO;
        } else if (flag.toLowerCase().equals("no")) {
            colNameSrc = ClusteringTable.ClUSTERINGINFO_COLUMN_NO;
            colNameDes = ClusteringTable.ClUSTERINGINFO_COLUMN_YES;
        } else {
            log.info("[PeopleInSearchService] ignoreClustering Param flag is error, it must be yes or no");
            return false;
        }
        List<PeopleInAttribute> listSrc = hBaseDao.getClustering(region, time, colNameSrc);
        List<PeopleInAttribute> listDes = hBaseDao.getClustering(region, time, colNameDes);
        Iterator<PeopleInAttribute> iterator = listSrc.iterator();
        PeopleInAttribute peopleInAttribute;
        while (iterator.hasNext()) {
            peopleInAttribute = iterator.next();
            if (clusterId.equals(peopleInAttribute.getClusteringId())) {
                peopleInAttribute.setFlag(flag);
                listDes.add(peopleInAttribute);
                iterator.remove();
            }
        }
        boolean booSrc = hBaseDao.putClustering(region, time, colNameSrc, listSrc);
        boolean booDes = hBaseDao.putClustering(region, time, colNameDes, listDes);
        return booSrc && booDes;

    }

    /**
     * 单个聚类轨迹
     *
     * @param time 时间月份
     * @param region 区域ID
     * @param clusterId 聚类ID
     * @param flag 忽略聚类标志
     * @return 成功 & 失败
     */
    public List<Locus> locusClustering(String time, String region, String clusterId, String flag) {

        byte[] colName;
        if (flag.toLowerCase().equals("yes")) {
            colName = ClusteringTable.ClUSTERINGINFO_COLUMN_NO;
        } else if (flag.toLowerCase().equals("no")) {
            colName = ClusteringTable.ClUSTERINGINFO_COLUMN_YES;
        } else {
            log.info("[PeopleInSearchService] locusClustering Param flag is error, it must be yes or no");
            return null;
        }
        List<PeopleInAttribute> peopleInAttributeList = hBaseDao.getClustering(region, time, colName);
        if(peopleInAttributeList != null) {
            for(PeopleInAttribute attribute : peopleInAttributeList) {
                if(clusterId.equals(attribute.getClusteringId())) {
                    HashMap<String, Integer> mapCount = new HashMap<>();
                    HashMap<String, String> mapTime = new HashMap<>();
                    HashSet<String> ipcIdSet = new HashSet<>();
                    List<String> rowKeys = attribute.getRowKeys();
                    if(rowKeys != null) {
                        for(String rowKey : rowKeys) {
                            String ipcId = rowKey.split("_")[0];
                            String addTime = rowKey.split("_")[1];
                            ipcIdSet.add(ipcId);
                            mapTime.put(ipcId, addTime);
                            mapCount.put(ipcId, mapCount.getOrDefault(ipcId, 0)+1);
                        }
                        ArrayList<String> ipcIds = new ArrayList<>(ipcIdSet);
                        log.info("[PeopleInSearchService] locusClustering ipcIds : " + ipcIds);
                        Map<String, DeviceDTO> deviceInfo = deviceQueryService.getDeviceInfoByBatchIpc(ipcIds);
                        ArrayList<Long> deviceIds = new ArrayList<>();
                        if(deviceInfo != null) {
                            for (Map.Entry<String, DeviceDTO> entry : deviceInfo.entrySet()) {
                                deviceIds.add(Long.valueOf(entry.getValue().getId()));
                            }
                        }
                        log.info("[PeopleInSearchService] locusClustering deviceIds : " + deviceIds);
                        Map<Long, WebgisMapPointDTO> deviceMap = deviceQueryService.getDeviceInfoByBatchIdByDevice(deviceIds);
                        if(ipcIds != null) {
                            List<Locus> locusList = new ArrayList<>();
                            for (String ipcId : ipcIds) {
                                Locus locus = new Locus();
                                locus.setTime(mapTime.getOrDefault(ipcId, new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(System.currentTimeMillis())));
                                locus.setCount(mapCount.getOrDefault(ipcId, 0));
                                if(deviceInfo != null) {
                                    locus.setDeviceId(Long.valueOf(deviceInfo.get(ipcId).getId()));
                                }
                                if(deviceMap != null) {
                                    locus.setMarsLatitude(deviceMap.get(Long.valueOf(deviceInfo.get(ipcId).getId())).getMarsLatitude());
                                    locus.setMarsLongitude(deviceMap.get(Long.valueOf(deviceInfo.get(ipcId).getId())).getMarsLongitude());
                                }
                                locusList.add(locus);
                            }
                            return locusList;

                        }
                    }
                }
            }
        }
        return null;

    }

}