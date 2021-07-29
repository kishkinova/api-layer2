# Communication between Client, Discovery service, and Gateway

This document is a summary of knowledge gathered during the implementation of PassTickets and includes how to 
short-cut delays in registration process (implemented as a side-effect).

## Client and discovery service (server)

To begin communication between the client and the Discovery service, the client should register with the discovery server. Minimum information requirements from the client to communicate to the server include the following:

- **serviceId (or applicationId)**
- **instanceId** 
    - Ensure that the instanceId is unique. Typically this is concatenated from `serviceId`, `hostname`, and `port`. Note that `hostname` and `port` should be unique themselves.
    - The structure of the instanceId is typically `${hostname}:${serviceId}:${port}`. The third part could also be a random number or string.
 - **health URL**
    - The URL where the service responds with the status of client
    - The discovery client can check this state but active registration occurs via heart beats.
 - **timeout about heartbeat**
    - The client defines how often a heartbeat is sent and when it is time to unregister.
    - The client's active registration with the Discovery service is maintained until a heartbeat is not delivered within the timeout setting or the client unregisters itself.  
 - **service location information**
    - Service location information is used for callback and includes `IP`, `hostname`, `port`, and `securePort` for HTTPS.
    - `VipAddress` can be used and is usually the same as the `serviceId`.
 - **information about environment (shortly AWS or own)**
 - **status**
    - In the registration it replaces the first heartbeat
 - **other information** (Optional)
    - Other parameters can be included, but do not affect communication.
    - Customized data outside of the scope of Eureka can be stored in the metadata.
    - Note: Metadata aren't data for one time use, and can changed after registration. However, a REST method can be used to update these metadata. Other data cannot be changed after registration

After registration, the client sends a heartbeat. Heart beat renews (extends) registration on discovery server. Missing heart beat longer than timeout will unregister service automatically.

The client can drop communication by unregistering with the Discovery service. Unregistering the client simply speeds up the process of client removal. 
Without the un-registration call, the Discovery service waits for the heartbeat to time out. 
Note: This timeout is longer than the interval of the renewal of client registration via the heartbest.

Typically, all communication is covered with caches. As such, a client cannot be used immediately
after registration. Caching occurs in many places for the system as a whole and it takes time to go through all of them. 
Typically, there is a thread pool whereby one thread periodically updates caches. All caches are independent and do not affect other caches.  

## Caches

The following section describes all the caches. The main idea is to shortcut
the process of registration and un-registration to allow the use of caches on the Gateway side. As such, the race condition between
different settings in Discovery Services and Gateways is avoided. Descriptions of caches also include a link to the solution describing how it is solved.

### Discovery service & ResponseCache

`ResponseCache` is implemented on the Discovery Service. This cache is responsible for minimizing the call's overhead of
registered instances. If any application calls the Discovery Service, initially the cache is used or a record is created for subsequent calls.

The default for this cache contains two spaces: `read` and `readWrite`. Other applications look in `read`. If the
record is missing, it looks in `readWrite` or recreates a record. `Read` cache updates via an internal thread which 
periodically compares records in both spaces (on references level, null including).

<font color = "red"> If values are different thread copies records from `readWrite` into `read` space. </font>

The two spaces was evidently created for Netflix purposes and was changed with pull 
request https://github.com/Netflix/eureka/pull/544. This improvement allows configuration to use only 
`readWrite` space, `read` will be ignored and the user looks directly to `readWrite`.

In default setting (read cache is on), the Discovery Service evicts on registration and un-registration records about service, delta and full registry only in `readWrite` space. 
`Read` still contains old record until the refresh thread ends.
Disabling the `read` cache allows reading directly from `readWrite`. This removes delay.
 <font color = "red"> This description needs to be refactored to improve clarity.</font>

```
eureka:
    server:
        useReadOnlyResponseCache: false
```

### Gateway & Discovery Client

On the Gateway's side there is a discovery client which supports queries about services and instances on the gateway side. It will
cache all information from the Discovery Service and serve the old cached data.
Asynchronous threads update cached data; from time to time fetch new registries to fetch new data. 
Updating can be performed either as delta, or full.

The full update fetch is the initial fetch as it is necessary to download all required information, after that it is rarely used due to performance. 
The Gateway could call the full update, but it happens only if data are not correct to fix them. One possible reason could be the long delay between fetching.

Delta fetching loads just the last changes. Delta is not related to a specific Gateway, it doesn't return differences since the last call. 
Discovery service collects changes in registry (client registration and
cancellation) and store them into queue. This queue is served to Gateways. They detect what is new and update its own registry copy (one gateway can get the same information many times).
The queue on discovery service contains information about changes for a period (as default it is 180s). The queue is periodically cleaned by separated thread (other asynchronous task). 
Cleaning removes all information about changes older than configuration.
For easy detection of new updates by gateway there is a mechanism to store version to each update (incrementing number).

This cache is minimized by allowing asynchronous fetches at any time. The following classes are used:

- **ApimlDiscoveryClient**
    - Custom implementation of discovery client.
    - Via reflection it takes reference to the thread pool responsible for fetching of registry.
    - Contains method ```public void fetchRegistry()```, which add new asynchronous command to fetch registry.
 - **DiscoveryClientConfig**
    - Configuration bean to construct the custom discovery client.
    - `DiscoveryClient` also support event, especially `CacheRefreshedEvent` after fetching.
    - It is used to notify other bean to evict caches (route locators, ZUUL handle mapping, `CacheNotifier`).
 - **ServiceCacheController**
    - The controller to accept information about service changes (ie. new instance, removed instance).
    - This controller asks `ApimlDiscoveryClient` to fetch (it makes a delta and sends an event).
    - After this call, the process is asynchronous and cannot be directly checked (only by events).
    
### Gateway & Route locators

The gateway includes the bean `ApimlRouteLocator`. This bean is responsible for collecting the client's routes. It indicates that information 
is available about the path and services. This information is required to map the URI to a service. The most important is
the filter `PreDecorationFilter`. It calls the method ```Route getMatchingRoute(String path)``` on the locator to translate the URI into
information about the service. A filter then stores information about the service (i.e. `serviceId`) into the ZUUL context. 

In our implementation we use a custom locator, which adds information about static routing. There is possible to have multiple locators. All of them 
could be collected by `CompositeRouteLocator`. Now `CompositeRouteLocator` contains `ApimlRouteLocator` and a default implementation. Implementation of static routing
could also be performed by a different locator (it is not necessary to override locator based on `DiscoveryClient`). Similarly, a super class of 
`ApimlRouteLocator` uses `ZuulProperties`. This can be also be used to store a static route. 

**Note:** To replace `ApimlRouteLocator` with multiple locators is only for information, and it could be changed in the future.

**solution**

Anyway this bean should be evicted. It is realized via event from fetching registry (implemented in DiscoveryClientConfig) and
call on each locator method refresh(). This method call discoveryClient and then construct location mapping again. Now after 
fetching new version of registry is constructed well, with new services.

### Gateway & ZuulHandlerMapping

This bean serve method to detect endpoint and return right handler. Handlers are created on the begin and then just looked up
by URI. In there is mechanism of dirty data. It means, that it create handlers and they are available (don't use locators) 
until they are mark as dirty. Then next call refresh all handlers by data from locators.

**solution**

In DiscoveryClientConfig is implemented listener of fetched registry. It will mark ZuulHandlerMapping as dirty.

### Ribbon load balancer

On the end of ZUUL is load balancer. For that we use Ribbon (before speed up implementation it was `ZoneAwareLoadBalancer`).
Ribbon also has its own cache that stores information about instances. Shortly, ZUUL give to Ribbon request and it should 
send to an instance. ZUUL contains information about servers (serviceId -> 1-N instances) and information about state of load
balancing (depends on selected mechanism - a way to select next instance). If this cache is not evicted, Ribbon can try to send
a request to the server that was removed, don't know any server to send or just overload an instance, because don't know about other.
Ribbon can throw many exceptions in this time, and it is not sure, that it retries sending in right way.

**solution**

Now we use as load balancer implementation `ApimlZoneAwareLoadBalancer` (it extends original `ZoneAwareLoadBalancer`). This
implementation only add method ```public void serverChanged()``` which call super class to reload information about servers,
it means about instances and their addresses.

Method serverChanged is called from `ServiceCacheEvictor` to be sure, that before custom EhCaches are evicted and load balancer get right 
information from ZUUL.

### Service cache - our custom EhCache

For our own purpose EhCache was added, which can collect much information about processes. It is highly recommended to synchronize the
state of EhCache with the discovery client. If not, it is possible to use old values (i.e. before registering new service's 
instance with different data than old one). It can make many problems in logic (based on race condition).

It was reason to add `CacheServiceController`. This controller is called from discovery service (exactly from
`EurekaInstanceRegisteredListener` by event `EurekaInstanceRegisteredEvent`). For cleaning caches gateway uses interface
`ServiceCacheEvict`. It means each bean can be called about any changes in registry and evict EhCache (or different cache).

Controller evict all custom caches via interface `ServiceCacheEvict` and as `ApimlDiscoveryClient` to fetch new registry. After
than other beans are notified (see `CacheRefreshedEvent` from discovery client).

This mechanism is working, but not strictly right. There is one case:

1. Instance changed in discovery client.
2. Gateway are notified, clean custom caches and ask for new registry fetching.
3. ZUUL accepts new request and make a cache (again with old state) - **this is wrong**
4. Fetching of registry is done, evict all Eureka caches.

For this reason there was added new bean `CacheEvictor`.
 
#### CacheEvictor

This bean collects all calls from `CacheServiceController` and waits for registry fetching. On this event it will clean all
custom caches (via interface `ServiceCacheEvict`). On the end it means that custom caches are evicted twice (before Eureka parts
and after). It fully supports right state.

## Other improvements

Implementation of this improvement wasn't just about caches, but discovery service contains one bug with notification. 

### Event from InstanceRegistry

In the Discovery Service bean `InstanceRegistry` exists. This bean is called for register, renew and unregister of service (client). 
Unfortunately, this bean contains one problem; it notifies about newly registered instances before it registers it, 
similarly with unregister (cancellation) and renew. It doesn't matter about renew (it is not a change), but the others create a problem. We
can clean caches before update in `InstanceRegistry` happened. On this topic exists a [Spring Cloud Netflix issue](https://github.com/spring-cloud/spring-cloud-netflix/issues/2659).

This issue takes a long time, and it is not good wait for implementation, for this reason `ApimlInstanceRegistry` was implemented.
This bean replaces implementation and notifies in the right order. It is via Java reflection and it will be removed when
Eureka is fixed. 

## Using caches and their evicting 

If you use anywhere custom cache, implement interface ServiceCacheEvict to evict. It offers two methods:
- `public void evictCacheService(String serviceId)`
    - To evict only part of caches related to one service (multiple instances with same serviceId).
    - If there is no way how to do it, you can evict all records
- `public void evictCacheAllService()`
    - To evict all records in the caches, which can have a relationship with any service.
    - This method will be call very rare, only in case that there is impossible to get serviceId (ie. wrong format of instanceId).

## Order to clean caches

From the Instance registry, information is distributed in this order:

```
Discovery service > ResponseCache in discovery service > Discovery client in gateway > Route locators in gateway > ZUUL handler mapping
```

After this chain is our EhCache (because this is first time, which could cache new data).

From the user point of view after ZUUL handler mapping is the Ribbon load balancer cache.

---

**REST API**

```
There is possible to use REST API, described at https://github.com/Netflix/eureka/wiki/Eureka-REST-operations.
``` 
 
