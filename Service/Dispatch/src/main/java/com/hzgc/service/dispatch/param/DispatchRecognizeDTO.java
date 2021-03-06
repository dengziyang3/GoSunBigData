package com.hzgc.service.dispatch.param;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.io.Serializable;

@ApiModel(value = "布控告警历史记录入参封装")
@Data
public class DispatchRecognizeDTO implements Serializable {
    @ApiModelProperty(value = "查询类型（0：抓拍，1：识别，3：白名单，4：活体检测）")
    @NotNull
    private int searchType;
    @ApiModelProperty(value = "查询区域")
    @NotNull
    private Long regionId;
    @ApiModelProperty(value = "开始查询时间")
    private String startTime;
    @ApiModelProperty(value = "结束查询时间")
    private String endTime;
    @ApiModelProperty(value = "起始行数")
    @NotNull
    private int start;
    @ApiModelProperty(value = "分页行数")
    @NotNull
    private int limit;
}
