/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */
package org.zowe.apiml.gateway.security.service.zosmf;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.discovery.DiscoveryClient;
import lombok.Data;
import lombok.Value;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.zowe.apiml.gateway.security.service.ServiceCacheEvict;
import org.zowe.apiml.gateway.security.service.ZosmfService;
import org.zowe.apiml.security.common.config.AuthConfigurationProperties;

import javax.annotation.PostConstruct;
import java.util.List;

/**
 * This bean is default implementation of access to z/OSMF authentication. It collects all other implementation and
 * select right implementation by z/OSMF version. This bean is facade for those implementations and all its own methods
 * are delegated to implementation based of z/OSMF version.
 * <p>
 * see also:
 * - {@link ZosmfServiceV2}
 * - new version supporting https://www.ibm.com/support/knowledgecenter/SSLTBW_2.4.0/com.ibm.zos.v2r4.izua700/izuprog_API_WebTokenAuthServices.htm
 * - {@link ZosmfServiceV1}
 * - old version using endpoint /zosmf/info
 */
@Primary
@Service
public class ZosmfServiceFacade extends AbstractZosmfService implements ServiceCacheEvict {

    protected final ApplicationContext applicationContext;
    protected final List<ZosmfService> implementations;

    /**
     * This attribute is used for calling of methods in the bean, which are covered by AOP (cache). Direct call will
     * bypass those AOP.
     */
    private ZosmfServiceFacade meProxy;

    public ZosmfServiceFacade(
        final AuthConfigurationProperties authConfigurationProperties,
        final DiscoveryClient discovery,
        final @Qualifier("restTemplateWithoutKeystore") RestTemplate restTemplateWithoutKeystore,
        final ObjectMapper securityObjectMapper,
        final ApplicationContext applicationContext,
        final List<ZosmfService> implementations
    ) {
        super(
            authConfigurationProperties,
            discovery,
            restTemplateWithoutKeystore,
            securityObjectMapper
        );
        this.applicationContext = applicationContext;
        this.implementations = implementations;
    }

    @PostConstruct
    public void afterPropertiesSet() {
        meProxy = applicationContext.getBean(ZosmfServiceFacade.class);
    }

    /**
     * It evicts all records in caches used for z/OSMF (information about version, domain and current used
     * implementation).
     */
    @CacheEvict(value = {"zosmfInfo", "zosmfServiceImplementation"}, allEntries = true)
    public void evictCaches() {
        // evict all caches
    }

    /**
     * Method return base information about z/OSMF which is currently in use. Method use cache to reduce amount of calls.
     *
     * @param zosmfServiceId id of z/OSMF service (see static definition)
     * @return ZosmfInfo, which contains version of z/OSMF, domain and realm (domain)
     */
    @Cacheable("zosmfInfo")
    public ZosmfInfo getZosmfInfo(String zosmfServiceId) {
        final String url = getURI(zosmfServiceId) + ZOSMF_INFO_END_POINT;
        final HttpHeaders headers = new HttpHeaders();
        headers.add(ZOSMF_CSRF_HEADER, "");

        try {
            final ResponseEntity<ZosmfInfo> info = restTemplateWithoutKeystore.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers), ZosmfInfo.class
            );

            ZosmfInfo zosmfInfo = info.getBody();
            if ((zosmfInfo != null) && StringUtils.isEmpty(zosmfInfo.getSafRealm())) {
                apimlLog.log("apiml.security.zosmfDomainIsEmpty", ZOSMF_DOMAIN);
                throw new AuthenticationServiceException("z/OSMF domain cannot be read.");
            }

            return zosmfInfo;
        } catch (RuntimeException re) {
            meProxy.evictCaches();
            throw handleExceptionOnCall(url, re);
        }
    }

    /**
     * Method returns version of current z/OSMF. It uses information from {@link #getZosmfInfo(String)}. If version
     * is not available return 0 (as version which doesn't support method to get this information)
     * @param zosmfInfo value which can be get with {@link #getZosmfInfo(String)} to extract version
     * @return version of z/OSMF or 0 as default
     */
    protected int getVersion(ZosmfInfo zosmfInfo) {
        if (zosmfInfo == null) return 0;
        return zosmfInfo.getVersion();
    }

    /**
     * It return implementation which should be used with current version of z/OSMF. It is determined by version of
     * z/OSMF (see {@link #getZosmfInfo(String)}).
     * Method use cache to fast response.
     * @param zosmfServiceId id of z/OSMF's service (see static definition)
     * @return ImplementationWrapper, which contains implementation and ZosmfInfo (version and realm/domain)
     */
    @Cacheable("zosmfServiceImplementation")
    public ImplementationWrapper getImplementation(String zosmfServiceId) {
        final ZosmfInfo zosmfInfo = meProxy.getZosmfInfo(zosmfServiceId);
        final int version = getVersion(zosmfInfo);
        for (final ZosmfService zosmfService : implementations) {
            if (zosmfService.isSupported(version)) return new ImplementationWrapper(zosmfInfo, zosmfService);
        }

        meProxy.evictCaches();
        throw new IllegalArgumentException("Unknown version of z/OSMF : " + version);
    }

    /**
     * @return implementation for current version of z/OSMF defined in configuration.
     */
    protected ImplementationWrapper getImplementation() {
        return meProxy.getImplementation(getZosmfServiceId());
    }

    @Override
    public AuthenticationResponse authenticate(Authentication authentication) {
        final ImplementationWrapper implementation = getImplementation();
        final ZosmfInfo zosmfInfo = implementation.getZosmfInfo();
        final AuthenticationResponse output = implementation.getZosmfService().authenticate(authentication);
        if (zosmfInfo != null) {
            output.setDomain(zosmfInfo.getSafRealm());
        }
        return output;
    }

    @Override
    public void validate(TokenType type, String token) {
        getImplementation().getZosmfService().validate(type, token);
    }

    @Override
    public void invalidate(TokenType type, String token) {
        getImplementation().getZosmfService().invalidate(type, token);
    }
    @Override
    public boolean isSupported(int version) {
        return false;
    }

    @Override
    public void evictCacheAllService() {
        meProxy.evictCaches();
    }

    @Override
    public void evictCacheService(String serviceId) {
        if (StringUtils.equalsIgnoreCase(getZosmfServiceId(), serviceId)) {
            meProxy.evictCaches();
        }
    }

    /**
     * DTO with base information about z/OSMF (version and realm/domain)
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ZosmfInfo {

        @JsonProperty("zosmf_version")
        private int version;

        @JsonProperty("zosmf_full_version")
        private String fullVersion;

        @JsonProperty(ZOSMF_DOMAIN)
        private String safRealm;

    }

    /**
     * DTO about implementation. It contains instance of bean implements ZosmfService and base information about
     * z/OSMF (version and realm/domain). DTO is using to specify current z/OSMF and implementation, which support it.
     */
    @Value
    public static class ImplementationWrapper {

        private final ZosmfInfo zosmfInfo;

        private final ZosmfService zosmfService;

    }

}
