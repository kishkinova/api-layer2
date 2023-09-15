package org.zowe.apiml.cloudgatewayservice.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.zowe.apiml.cloudgatewayservice.service.CentralApimlInfoMapper;
import org.zowe.apiml.cloudgatewayservice.service.GatewayIndexService;
import org.zowe.apiml.cloudgatewayservice.service.model.ApimlInfo;
import org.zowe.apiml.message.log.ApimlLogger;
import org.zowe.apiml.product.logging.annotations.InjectApimlLogger;
import org.zowe.apiml.services.ServiceInfo;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

import static com.google.common.base.Strings.emptyToNull;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
public class RegistryController {

    @InjectApimlLogger
    private final ApimlLogger apimlLog = ApimlLogger.empty();
    private final CentralApimlInfoMapper centralApimlInfoMapper;
    private final GatewayIndexService gatewayIndexService;

    @Value("${apiml.cloudGateway.serviceRegistryEnabled:false}")
    private boolean serviceRegistryEnabled;

    @GetMapping(value = {"/registry/", "/registry", "/registry/{apimlId}"})
    public Flux<ApimlInfo> getServices(@PathVariable(required = false) String apimlId, @RequestParam(name = "apiId", required = false) String apiId) {

        if (serviceRegistryEnabled) {
            Map<String, List<ServiceInfo>> apimlList = gatewayIndexService.listRegistry(emptyToNull(apimlId), emptyToNull(apiId));
            return Flux.fromIterable(apimlList.entrySet()).map(this::buildEntry).onErrorContinue(RuntimeException.class, (ex, consumer) -> log.debug("Unexpected mapping error", ex));
        }
        apimlLog.log("org.zowe.apiml.gateway.serviceRegistryDisabled");
        return Flux.empty();
    }

    private ApimlInfo buildEntry(Map.Entry<String, List<ServiceInfo>> entry) {
        return centralApimlInfoMapper.buildApimlServiceInfo(entry.getKey(), entry.getValue());
    }

}
