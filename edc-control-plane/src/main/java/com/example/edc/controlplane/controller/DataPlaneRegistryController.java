package com.example.edc.controlplane.controller;

import com.example.edc.controlplane.dto.DataPlaneInstanceResponse;
import com.example.edc.controlplane.dto.DataPlaneRegistrationRequest;
import com.example.edc.controlplane.service.ControlPlaneService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/dataplanes")
public class DataPlaneRegistryController {
    private final ControlPlaneService controlPlaneService;

    public DataPlaneRegistryController(ControlPlaneService controlPlaneService) {
        this.controlPlaneService = controlPlaneService;
    }

    /**
     * 注册 Data Plane 节点信息。
     *
     * @param request 注册请求，包含节点 ID 与对外地址。
     * @return 最新节点状态。
     */
    @PostMapping("/register")
    public DataPlaneInstanceResponse register(@Valid @RequestBody DataPlaneRegistrationRequest request) {
        return controlPlaneService.registerDataPlane(request);
    }

    /**
     * 查询已注册 Data Plane 列表。
     *
     * @return Data Plane 节点列表。
     */
    @GetMapping
    public List<DataPlaneInstanceResponse> list() {
        return controlPlaneService.listDataPlanes();
    }
}
