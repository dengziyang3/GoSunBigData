package com.hzgc.service.community.param;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;

@ApiModel(value = "聚焦人员抓拍返回封装类")
@Data
public class PeopleCaptureVO implements Serializable {
    @ApiModelProperty(value = "抓拍时间")
    private String captureTime;
    @ApiModelProperty(value = "相机抓拍设备")
    private String cameraDeviceId;
    @ApiModelProperty(value = "电围抓拍设备")
    private String imsiDeviceId;
    @ApiModelProperty(value = "抓拍照片")
    private String ftpUrl;
    @ApiModelProperty(value = "接收imsi")
    private String imsi;
}
