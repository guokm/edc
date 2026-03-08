package com.example.edc.dataplane.controller;

import com.example.edc.dataplane.dto.DataPullResponse;
import com.example.edc.dataplane.dto.TransferEdrResponse;
import com.example.edc.dataplane.dto.TransferSignalRequest;
import com.example.edc.dataplane.service.DataPlaneTransferService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class DataPlaneController {
    private final DataPlaneTransferService transferService;

    public DataPlaneController(DataPlaneTransferService transferService) {
        this.transferService = transferService;
    }

    /**
     * 启动传输流程。
     *
     * @param request 传输启动信令，包含 transferProcessId。
     * @return 启动结果及 EDR。
     */
    @PostMapping("/transfer/start")
    public Map<String, Object> start(@Valid @RequestBody TransferSignalRequest request) {
        return transferService.start(request.transferProcessId());
    }

    /**
     * 挂起传输流程。
     *
     * @param request 传输信令，包含 transferProcessId。
     * @return 更新后的状态。
     */
    @PostMapping("/transfer/suspend")
    public Map<String, Object> suspend(@Valid @RequestBody TransferSignalRequest request) {
        return transferService.updateState(request.transferProcessId(), "SUSPENDED");
    }

    /**
     * 恢复传输流程。
     *
     * @param request 传输信令，包含 transferProcessId。
     * @return 更新后的状态。
     */
    @PostMapping("/transfer/resume")
    public Map<String, Object> resume(@Valid @RequestBody TransferSignalRequest request) {
        return transferService.updateState(request.transferProcessId(), "RESUMED");
    }

    /**
     * 终止传输流程。
     *
     * @param request 传输信令，包含 transferProcessId。
     * @return 更新后的状态。
     */
    @PostMapping("/transfer/terminate")
    public Map<String, Object> terminate(@Valid @RequestBody TransferSignalRequest request) {
        return transferService.updateState(request.transferProcessId(), "TERMINATED");
    }

    /**
     * 查询传输流程对应的 EDR。
     *
     * @param transferProcessId 传输流程 ID。
     * @return EDR 响应。
     */
    @GetMapping("/transfer/edr/{transferProcessId}")
    public TransferEdrResponse resolveEdr(@PathVariable("transferProcessId") String transferProcessId) {
        return transferService.resolveEdr(transferProcessId);
    }

    /**
     * 基于 EDR 鉴权拉取传输数据。
     *
     * @param transferProcessId 传输流程 ID。
     * @param authorization EDR 鉴权令牌。
     * @param message 请求透传消息。
     * @return 拉取结果。
     */
    @GetMapping("/data/{transferProcessId}")
    public DataPullResponse pullData(
            @PathVariable("transferProcessId") String transferProcessId,
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @RequestParam(name = "message", required = false) String message) {
        return transferService.pullData(transferProcessId, authorization, message);
    }

    /**
     * 查询当前 Data Plane 节点运行摘要。
     *
     * @return 节点标识与传输统计。
     */
    @GetMapping("/dataplane/info")
    public Map<String, Object> info() {
        return transferService.info();
    }
}
