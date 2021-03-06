# Cloud Native Spring Batch

전체 소스코드는 [zacscoding/cloud-native-spring-batch](https://github.com/zacscoding/cloud-native-spring-batch) 에서 확인할 수 있습니다.

## Table Of Contents

- [Batch Service](#Batch-Service)
- [Spring Cloud](#Spring-Cloud)
  - [Spring Cloud Config](#Spring-Cloud-Config)
  - [Spring Cloud Eureka](#Spring-Cloud-Eureka)
  - [Circuit Breaker](#Circuit-Breaker)
- [예제 구성](#예제-구성)
- [시작 하기](#시작하기)

---

## Batch Service

해당 예제의 **Batch Service**는 `Foo`라는 도메인을 기준으로 아래와 같은 작업으로 이루어져있습니다.

```java
@Data
public class Foo {
    private String first;
    private String second;
    private String third;
    private String message;
}
```

![Batch Example](https://user-images.githubusercontent.com/25560203/141975285-f0a06600-cc98-4b5d-a467-9b7f86955711.png)  

- **DownloadingJobExecutionListener**: S3의 Bucket에 있는 `*.csv` 파일을 `/tmp` 디렉터리로 다운로드합니다.
  - 해당 파일들은 `ExecutionContext`에 `localFiles`라는 키 값으로 경로를 저장합니다.
- **ItemReader(MultiResourceItemReader)**: `ExecutionContext::get("localFiles")`에 있는 FlatFile을 읽습니다.
- **ItemProcessor(EnrichmentProcessor)**: `enrich-service`의 `GET /enrich`를 호출하여 Foo 도메인의 message 필드를 채워줍니다.
- **ItemWriter(JdbcItemWriter)**: Foo 도메인을 데이터베이스에 적재합니다.

해당 배치 서비스를 기준으로 Spring Cloud 모듈을 적용합니다(Config Server, Eureka Server)

그전에 Spring Cloud 컴포넌트에 대해 알아봅니다.

---  

## Spring Cloud

### Spring Cloud Config

[Spring Cloud Config](https://cloud.spring.io/spring-cloud-config/reference/html/) 는 설정 정보를 관리하는 모듈입니다.  

설정 정보를 변경하면 아래와 같은 문제가 발생합니다.

- 애플리케이션을 재시작 해야한다.
- 어떤 설정 정보가 수정되었는지 확인할 방법이 없고, 수정 내용을 이전 상태로 복구 할 방법이 없다(추적성이 없다).
- 설정이 여기저기 분산되어 있어서 어디에서 어떤 설정 정보를 변경해야 하는지 파악하기 어렵다.
- 손쉬운 압호화/복호화 방법이 없다.

이러한 문제들을 Spring cloud config를 통해서 해결할 수 있습니다.  

![Spring cloud config](https://user-images.githubusercontent.com/25560203/141975366-2638bb78-6d07-4af8-b1e3-1c8d1b7b2c00.png)

(**Spring Cloud Config Server/Client 구성**)  

<br />

![Spring cloud config workflow](https://user-images.githubusercontent.com/25560203/141975466-e2d9405a-1814-4343-9bd8-7195358e2508.png)
(**Spring Cloud Config 동작 방식**)


**Spring Clound Config Server**

**[EnvironmentRepository](https://github.com/spring-cloud/spring-cloud-config/blob/main/spring-cloud-config-server/src/main/java/org/springframework/cloud/config/server/environment/EnvironmentRepository.java)**  

```java

public interface EnvironmentRepository {

	Environment findOne(String application, String profile, String label);

	default Environment findOne(String application, String profile, String label, boolean includeOrigin) {
		return findOne(application, profile, label);
	}
}
```

위와 같이 `findOne(application, profile, label)` 라는 메소드를 통해 설정 정보를 조회합니다. 이때 구현체는 Git, JDBC, Vault, S3 등이 있습니다.

<br />

**[EnvironmentController](https://github.com/spring-cloud/spring-cloud-config/blob/main/spring-cloud-config-server/src/main/java/org/springframework/cloud/config/server/environment/EnvironmentController.java)**  

```java
@RestController
@RequestMapping(method = RequestMethod.GET,
		path = "${spring.cloud.config.server.prefix:}")
public class EnvironmentController {
	private EnvironmentRepository repository;
    ...
	@RequestMapping(path = "/{name}/{profiles:.*[^-].*}",
			produces = MediaType.APPLICATION_JSON_VALUE)
	public Environment defaultLabel(@PathVariable String name,
			@PathVariable String profiles) {
		return getEnvironment(name, profiles, null, false);
	}

	@RequestMapping(path = "/{name}/{profiles:.*[^-].*}",
			produces = EnvironmentMediaType.V2_JSON)
	public Environment defaultLabelIncludeOrigin(@PathVariable String name,
			@PathVariable String profiles) {
		return getEnvironment(name, profiles, null, true);
	}

	@RequestMapping(path = "/{name}/{profiles}/{label:.*}",
			produces = MediaType.APPLICATION_JSON_VALUE)
	public Environment labelled(@PathVariable String name, @PathVariable String profiles,
			@PathVariable String label) {
		return getEnvironment(name, profiles, label, false);
	}

	@RequestMapping(path = "/{name}/{profiles}/{label:.*}",
			produces = EnvironmentMediaType.V2_JSON)
	public Environment labelledIncludeOrigin(@PathVariable String name,
			@PathVariable String profiles, @PathVariable String label) {
		return getEnvironment(name, profiles, label, true);
	}        
    ...
}   
```

기본적으로 아래와 같은 2가지 Path를 통해 Environment(설정 정보)를 조회할 수 있습니다.

- `/{spring.cloud.config.server.prefix}/{name}/{profiles}`
- `/{spring.cloud.config.server.prefix}/{name}/{profiles}/{label}`, 여기서 label은 Git의 경우 master 브런치, 커밋 해시 등


Spring cloud config server는 아래와 같이 `application.yaml`을 구성할 수 있습니다.

```yaml
server:
  port: 8888

spring:
  application:
    name: "config-server"
  cloud:
    config:
      server:
        prefix: /config-server
        git:
          # default 0, 매 요청마다 fetch
          # 단위는 Second 이며 (refresh > 0 && (now() - lastRefresh) < (refreshRate * 1000) 면 fetch 하지 않음
          refreshRate: 5
          # uri: https://github.com/evagova/config-repo
          uri: file://${user.home}/config-repo

---
spring:
  profiles: jdbc
  datasource:
    url: jdbc:h2:mem:testdb?DB_CLOSE_ON_EXIT=FALSE
    username: root
    password: root
    driver-class-name: org.h2.Driver
  cloud:
    config:
      server:
        jdbc:
          sql: SELECT KEY, VALUE from PROPERTIES where APPLICATION=? and PROFILE=? and LABEL=?
          order: 1

---
spring:
  profiles: rabbitmq
  cloud:
    bus:
      env:
        enabled: true
      refresh:
        enabled: true
  rabbitmq:
    host: 127.0.0.1
    port: 5672
    username: user
    password: secret
management:
  endpoints:
    web:
      base-path: /actuator
      exposure:
        include: ['bus-refresh']
```

---  

**Spring Cloud Config Client**


Spring cloud config client는 아래와 같이 `application.yaml`을 구성할 수 있습니다.


```yaml
spring:
  profiles:
    active: {ENV}
  application:
    name: demo
  cloud:
    config:
      uri: http://localhost:8888/config-server

management:
  endpoints:
    web:
      exposure:
        include: "refresh"
```

최초에 설정 정보를 조회하고 `@RefreshScope` 기능을 통해 설정 정보를 갱신할 수 있습니다.

- actuator의 `/actuator/refresh` 호출
- 직접 refresh 코드 호출

> @RefreshScope 사용 예제

```java
import org.springframework.cloud.context.config.annotation.RefreshScope;
...

@Slf4j
@RestController
@RefreshScope // 새로고침 가능
public class RefreshRestController {

    private final String message;

    // Environment 추상화 덕분에 PropertySource에서 가져오는 방식 그대로 읽어올 수 있음
    @Autowired
    public RefreshRestController(@Value("${application.message}") String message) {
        logger.warn("## RefreshController() is called. message : {}", message);
        this.message = message;
    }

    @GetMapping("/message")
    public String getMessage() {
        return message;
    }
}
```

> @EventListener를 통한 refresh 이벤트 리스너 예제

```java
@Component
public class CustomRefreshListener {

    @EventListener
    public void refresh(RefreshScopeRefreshedEvent e) {
        logger.warn("refresh event occur. name : {} / source : {}", e.getName(), e.getSource());
    }
}
```

> Custom refresh event publisher 예제

```java
import org.springframework.cloud.context.refresh.ContextRefresher;
import org.springframework.cloud.endpoint.RefreshEndpoint;

...

/**
 * See {@link RefreshEndpoint}
 */
@Slf4j
@AllArgsConstructor
@RestController
public class RefreshController {

    private final ContextRefresher contextRefresher;

    @PostMapping("/refresh")
    public Set<String> refresh() {
        logger.warn("## custom /refresh is called");
        return contextRefresher.refresh();
    }
}
```


**Spring Cloud Config with bus**

![spring cloud config with bus](https://user-images.githubusercontent.com/25560203/141975529-2bd1d5d0-69a0-46e1-9c31-5a8a5958576b.png)

Spring cloud stream(rabbitmq, kafka, reactor project 등)을 통해 다양한 메시징 방식을 적용할 수 있습니다.


---  

### Spring Cloud Eureka

Eureka는 Service Discovery 중 하나이며 Spring Cloud에서는 `Eureka`, `CloudFoundry`, `Consul`, `Zookeeper` 등을 제공합니다.

![Eureka Architecture](https://user-images.githubusercontent.com/25560203/141975569-14658e05-40ed-4362-bee8-3406fe7ace3a.png)

위와 같이 Eureka Server/Client가 존재합니다.

- ApplicationService는 Eureka Client를 이용하여 Eureka Server에 자신을 등록한다(즉 ServiceDiscovery의 대상이 된다)
- ApplicationClient는 `Get Registry`를 호출하여 등록된 서버 정보들을 찾는다(즉 ServiceDiscovery의 대상이 아니다)

**Eureka Communication**  

> **Note:** [Understanding eureka client server communication](https://github.com/Netflix/eureka/wiki/Understanding-eureka-client-server-communication)를 통해 자세한 내용을 확인할 수 있습니다.

- **Register**: Client -> Server에게 등록 요청을 보낸 후 ServiceDiscovery의 대상이 된다.
- **Renew**: 30초마다 heatbeat를 보내서 lease를 갱신한다. Server는 마지막 heatbeat를 보낸 시간보다 90초가 지났으면 해당 서비스를 Registry에서 제거한다.
- **Fetch Registry**: Client는 Server로부터 registry 정보를 가져와 로컬 캐시에 담아둔다. 이러한 registry 정보는 service discovery하는데 사용된다. <br />
아래의 com.netflix.discovery.DiscoveryClient를 살펴보면 delta를 조회하여 client의 이전 delta와 해시코드(문자열)를 비교하여 변경사항이 있으면 갱신한다.

```java
private void getAndUpdateDelta(Applications applications) throws Throwable {
    long currentUpdateGeneration = fetchRegistryGeneration.get();

    Applications delta = null;
    // /apps/delta?regions=... 호출
    EurekaHttpResponse<Applications> httpResponse = eurekaTransport.queryClient.getDelta(remoteRegionsRef.get());
    if (httpResponse.getStatusCode() == Status.OK.getStatusCode()) {
        delta = httpResponse.getEntity();
    }

    if (delta == null) {
        logger.warn("The server does not allow the delta revision to be applied because it is not safe. "
                + "Hence got the full registry.");
        getAndStoreFullRegistry();
    } else if (fetchRegistryGeneration.compareAndSet(currentUpdateGeneration, currentUpdateGeneration + 1)) {
        logger.debug("Got delta update with apps hashcode {}", delta.getAppsHashCode());
        String reconcileHashCode = "";
        if (fetchRegistryUpdateLock.tryLock()) {
            try {
                // client의 delta 갱신
                updateDelta(delta);
                // {instance_name}:{count}_{instance_name}_{count} ...
                reconcileHashCode = getReconcileHashCode(applications);
            } finally {
                fetchRegistryUpdateLock.unlock();
            }
        } else {
            logger.warn("Cannot acquire update lock, aborting getAndUpdateDelta");
        }
        ...
    }
    ...
}
```
- **Cancel**: Client는 애플리케이션 종료 시 Server에게 Cancel 요청을 보내 registry에서 제거된다.


**Eureka interacts example**

![Eureka Interacts](https://user-images.githubusercontent.com/25560203/141975638-26b6f37f-344e-4d4a-a248-37ca5665f0db.png)

- **Service1** : 애플리케이션 시작 시 Eureka 서버에서 Register 요청을 보낸 뒤 서버는 Registry에 추가한다.
- **Service2** : 주기적으로 Heatbeat를 보낸다.(Renew)  
- **Service3** : 애플리케이션 종료 시 Eureka 서버에게 Cancel 요청을 보낸 뒤 서버는 Registry에서 제거한다.  
- **Service4** : 애플리에키션 종료 시 Cancel 요청을 보내지 않은 상태이다(강제 종료)
- **Eureka Server** : Service4에 대하여 마지막 Heatbeat로 부터 90초가 지나서 Registry에서 제거한다.  

더 자세한 Endpoint는 [WIKI-Eureka-REST-operations](https://github.com/Netflix/eureka/wiki/Eureka-REST-operations)에서 확인할 수 있습니다.

**Eureka Server HA**  

HA 구성을 위해 Eureka Server도 다중으로 구성해야합니다. 이때 어떻게 서버간 통신하는지 살펴보겠습니다.

![Eureka Peer Awareness](https://user-images.githubusercontent.com/25560203/103169773-2940e800-4882-11eb-964f-3ff8ee5c42b3.png)

- Eureka Cluster에서 Eureka Server는 **peer** 라고 표현하고 아래와 같은 행위를 **Peer Awareness** 라고 부른다.
- 각각의 Eureka Server는 **Eureka Client** 를 이용하여 **Register**, **Fetch Registry** 작업이 이루어진다.
- **Register**, **Renew** 등 모든 Operation이 발생하면 다른 **Peer(Eureka Server)** 에게 동일한 요청(replica==true)을 시도한다.

아래와 같이 [PeerAwareInstanceRegistryImpl](https://github.com/Netflix/eureka/blob/master/eureka-core/src/main/java/com/netflix/eureka/registry/PeerAwareInstanceRegistryImpl.java)를 살펴보면 `Client` -> `Server`로의 `Register` 요청에 대하여 자신의 Registry를 업데이트하고  
`replicateToPeer()`를 통해 다른 Peer(Eureka Server)에게 `Register with replica` 요청을 보냅니다(이때 `POST /eureka/v2/app/appID`, 헤더에 Replica==true)

```java
@Singleton
public class PeerAwareInstanceRegistryImpl extends AbstractInstanceRegistry implements PeerAwareInstanceRegistry {
    ...
    @Override
    public void register(final InstanceInfo info, final boolean isReplication) {
        int leaseDuration = Lease.DEFAULT_DURATION_IN_SECS;
        if (info.getLeaseInfo() != null && info.getLeaseInfo().getDurationInSecs() > 0) {
            leaseDuration = info.getLeaseInfo().getDurationInSecs();
        }
        super.register(info, leaseDuration, isReplication);
        replicateToPeers(Action.Register, info.getAppName(), info.getId(), info, null, isReplication);
    }
    ...
}
```

---  

### Circuit Breaker

Spring Cloud에서는 CircuitBreaker를 적용하기 위해 [Hystrix](https://github.com/Netflix/Hystrix), [resilience4j](https://github.com/resilience4j/resilience4j),
[Sentinel](https://github.com/alibaba/Sentinel), [spring-retry](https://github.com/spring-projects/spring-retry) 를 제공합니다.

```java
@Service
public static class DemoControllerService {
	private RestTemplate rest;
	private CircuitBreakerFactory cbFactory;

	public DemoControllerService(RestTemplate rest, CircuitBreakerFactory cbFactory) {
		this.rest = rest;
		this.cbFactory = cbFactory;
	}

	public String slow() {
		return cbFactory.create("slow").run(() -> rest.getForObject("/slow", String.class), throwable -> "fallback");
	}
}
```

(출처: https://spring.io/projects/spring-cloud-circuitbreaker)

Circuit Breaker의 주요기능은 아래와 같습니다.

- Circuit Breaker
  - 메소드의 실행 성공/실패 결과를 기록하여 통계를 낸 후 Circuit open/close 여부를 결정한다
  - **(1) 일정 시간동안** **(2) 일정 개수 이상의 호출** 이 발생한 경우 **(3) 일정 비율 이상** 의 에러가 발생하면 Circuit Open
  - (4) 일정 시간 경과 후 **단 한개의 요청을 허용하며(Half Open)** 이 요청이 성공하면 Circuit Close
- Fallback
  - 실패한 경우(Exception) 사용자가 제공한 메소드를 대신 실행한다

코드로 살펴보면 아래와 같이 이용할 수 있습니다.

> hystrix example

```java
// (1) 어노테이션 기반
@HystrixCommand(commandKey = "ExtDep1", fallbackMethod="doSomething11")
public String doSomething() {
    // 다른 서버 API Call
}

public String doSomething11() {
    // fallback method 실행
}

// (2) HystrixCommand 상속
public class SampleCommand extends HystrixCommand<String> {
  @Override
  protected String run() {
    // 다른 서버 API Call
  }
}
```

> spring retry example

```java
public class EnrichmentProcessor {

    private final RestTemplate restTemplate;

    @Recover
    public Foo fallback(Foo foo) {
        foo.setMessage("error-fallback");
        return foo;
    }

    @CircuitBreaker(maxAttempts = 1, resetTimeout = 1000L)
    @Override
    public Foo process(@NotNull Foo foo) {
        final ResponseEntity<String> responseEntity = restTemplate.exchange(
                "http://enrich-service/enrich",
                HttpMethod.GET,
                null,
                String.class
        );
        foo.setMessage(responseEntity.getBody());
        return foo;
    }
}
```

> resilience4j

```java
final CircuitBreakerConfig circuitBreakerConfig =
        CircuitBreakerConfig.custom()
                            .failureRateThreshold(50)
                            .slidingWindowType(SlidingWindowType.COUNT_BASED)
                            .waitDurationInOpenState(Duration.ofMillis(5000))
                            .permittedNumberOfCallsInHalfOpenState(2)
                            .slidingWindowSize(2)
                            .recordExceptions(RestClientException.class, ResourceAccessException.class)
                            .build();

final CircuitBreakerRegistry circuitBreakerRegistry = CircuitBreakerRegistry.of(circuitBreakerConfig);
final CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("demo1");

message = circuitBreaker.executeSupplier(() -> return restTemplate.getForObject(endpoint, String.class));        
```

![Circuit Breaker](https://martinfowler.com/bliki/images/circuitBreaker/sketch.png)

<br />

![Circuit Breaker State](https://martinfowler.com/bliki/images/circuitBreaker/state.png)

(출처: https://martinfowler.com/bliki/CircuitBreaker.html)

---  

**Note** :thinking: Service Mesh

Spring Cloud에서는 Application에서 직접 Service Discovery, Resilience(retry, backoff, circuit breaker)를 적용합니다.

istio 같은 Service Mesh를 이용하여 인프라 레벨에서 위 문제를 해결할 수 있습니다.  

![istio archi](https://istio.io/latest/docs/ops/deployment/architecture/arch.svg)

(출처: https://istio.io/latest/docs/ops/deployment/architecture/)

---  

## 예제 구성

해당 예제에서는 아래와 같은 서비스를 구성하고 있고 `docker-compose`를 이용하여 각각의 서비스를 정의합니다.

> **Note**: 각 서비스의 도커 이미지는 `${service}/Dockerfile`에 정의되어 있습니다.

![Batch Examples](https://user-images.githubusercontent.com/25560203/141975806-234ba512-7091-4984-8803-7e99efe9fc2e.png))

### DB

> docker-compose.yaml

```yaml
  db:
    image: mysql:8.0.17
    container_name: db
    command: [ '--default-authentication-plugin=mysql_native_password', '--default-storage-engine=innodb' ]
    hostname: db
    environment:
      - MYSQL_ROOT_PASSWORD=p@ssw0rd
      - MYSQL_DATABASE=spring_batch
    ports:
      - "53306:3306"
```

<br />

### S3 구성하기

localstack을 이용하여 아래와 같이 s3 서비스를 컨테이너로 구동합니다.  
`aws-cli`를 이용하여 Bucket 생성 및 csv 파일을 업로드합니다.

> docker-compose.yaml

```yaml
  localstack:
    container_name: localstack
    image: localstack/localstack:0.11.4
    ports:
      - "8080:8080"
      - "4572:4572"
    hostname: localstack
    environment:
      - USE_SSL=0
      - SERVICES=s3
      - DATA_DIR=/tmp/localstack/data
      - DEBUG=1
      - DEFAULT_REGION=ap-southeast-2
      - DOCKER_HOST=unix:///var/run/docker.sock
      - PORT_WEB_UI=8080
    volumes:
      - /var/run/docker.sock:/var/run/docker.sock
      - ${TMPDIR:-/tmp/localstack}:/tmp/localstack

  init.s3:
    image: amazon/aws-cli
    entrypoint: /bin/sh -c
    container_name: init.s3
    command: "/s3/init.sh"
    environment:
      - ENVIRONMENT=LOCAL
    depends_on:
      - localstack
    volumes:
      - ${HOME}/.aws:/root/.aws
      - ./s3:/s3/input
      - ./s3/init.sh:/s3/init.sh
```

<br />

> init.sh

```shell
#!/usr/bin/env bash

echo "Delete bucket: spring-batch"
aws s3 rb s3://spring-batch --force --endpoint-url="http://localstack:4572" --region ap-southeast-2

echo "Create bucket: spring-batch"
aws s3 mb s3://spring-batch --endpoint-url="http://localstack:4572" --region ap-southeast-2

echo "Put objects"
for f in /s3/input/*.csv
do
    filename="$(cut -d'/' -f4 <<<"${f}")"
    aws s3api put-object --bucket spring-batch --key ${filename} --body ${f} --endpoint-url="http://localstack:4572" --region ap-southeast-2
done

```

---  

### config-server

> build.gradle

```shell
implementation 'org.springframework.cloud:spring-cloud-config-server'
```
<br />

> application.yaml

```yaml
spring:
  cloud:
    config:
      server:
        prefix: /config-server
        git:
          # default 0, 매 요청마다 fetch
          # 단위는 Second 이며 (refresh > 0 && (now() - lastRefresh) < (refreshRate * 1000) 면 fetch 하지 않음
          refresh-rate: 0
          uri: file://${user.home}/config-repo
``` 

<br />

> Main

```java
@SpringBootApplication
@EnableConfigServer
public class ConfigServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConfigServerApplication.class, args);
    }
}
```

<br />

> docker-compose.yaml

```java
  config-server:
    build:
      context: ../../config-server
      dockerfile: Dockerfile
    image: cloud-native-spring-batch/config-server
    hostname: config-server
    container_name: config-server
    ports:
      - "8888:8888"
    restart: always
    environment:
      - SPRING_CLOUD_CONFIG_SERVER_GIT_URI=file:///config-server/config-repo
      - LOGGING_LEVEL_ROOT=INFO
      - LOGGING_LEVEL_COM.GITHUB.ZACSCODING=DEBUG
    volumes:
      - ./config-repo:/config-server/config-repo
```

--- 

### eureka-server

> build.gradle

```shell
// eureka
implementation('org.springframework.cloud:spring-cloud-starter-netflix-eureka-server') {
    exclude group: 'org.springframework.cloud', module: 'spring-cloud-starter-ribbon'
    exclude group: 'com.netflix.ribbon', module: 'ribbon-eurek'
}
```

<br />

> application.yaml

```yaml
spring:
  application:
    name: eureka-server
  cloud:
    loadbalancer:
      ribbon:
        enabled: false

eureka:
  # dashboard에 대한 설정으로, http://localhost:3000/eureka-ui 를 통해 확인할 수 있다.
  dashboard:
    path: /eureka-ui
  instance:
    hostname: localhost
    statusPageUrlPath: /actuator/info
    healthCheckUrlPath: /actuator/health
  # 등록된 인스턴스 중 많은 수가 정해진 시간 내에 Heatbeat를 보내지 않으면 Eureka는 이를 인스턴스 문제가 아닌
  # 네트워크 문제라고 간주하고 Registry를 그대로 유지한다. Example 실행을 위해 false로 설정
  server:
    enableSelfPreservation: false
    # response cache
    responseCacheUpdateIntervalMs: 30000
  client:
    # Eureka client -> Eureka server로 등록 여부
    # standalone mode이므로 자기 자신을 등록할 필요가 없다.
    registerWithEureka: false
    # Eureka Client -> Eureka server로 Registry fetch 여부
    fetchRegistry: false

info:
  app:
    name: Account Example Application
    version: 1.0.0
    discription: This is a demo project for eurkea
```

<br />

> Main

```java
@Slf4j
@SpringBootApplication
@EnableEurekaServer
public class EurekaServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(EurekaServerApplication.class, args);
    }
}
```

<br />

> docker-compose.yaml

```yaml
  eureka-server:
    build:
      context: ../../eureka-server
      dockerfile: Dockerfile
    image: cloud-native-spring-batch/eureka-server
    hostname: eureka-server
    container_name: eureka-server
    ports:
      - "3000:3000"
    restart: always
    environment:
      - LOGGING_LEVEL_ROOT=WARN
    depends_on:
      - config-server
```

---  

### enrich-service

> application.yaml

```yaml
spring:
  application:
    name: enrich-service
  config:
    import: "configserver:http://localhost:8888/config-server"

eureka:
  instance:
    instance-id: ${spring.application.name}:${spring.application.instance_id:${random.value}}
    statusPageUrlPath: /actuator/info
    healthCheckUrlPath: /actuator/health
  client:
    service-url:
      defaultZone: http://localhost:3000/eureka/
    register-with-eureka: true
    fetch-registry: false

app:
  message: "Enrich"

management:
  endpoints:
    web:
      exposure:
        include: "refresh"
```

<br />

> Controller

```java
@Slf4j
@RestController
@RefreshScope
public class EnrichController {
    private int count = 0;
    private final String message;
    private final String instanceId;

    public EnrichController(@Value("${app.message}") String message,
                            @Value("${eureka.instance.instance-id}") String instanceId) {
        logger.info("Instantiate EnrichController with {}", message);
        this.message = message;
        this.instanceId = instanceId;
    }

    @GetMapping("/enrich")
    public String enrich() {
        this.count++;

        if (this.count % 10 == 0) {
            throw new RuntimeException("I screwed up");
        }

        return String.format("[%s] %s_%d", instanceId, message, count);
    }
}
```

<br />

> docker-compose.yaml

```yaml
  enrich-server1:
    build:
      context: ../../enrich-service
      dockerfile: Dockerfile
    image: cloud-native-spring-batch/enrich-service
    hostname: enrich-service1
    container_name: enrich-service1
    ports:
      - "9890:9890"
    restart: always
    environment:
      - SERVER_PORT=9890
      - SPRING_CONFIG_IMPORT=configserver:http://config-server:8888/config-server
      - EUREKA_CLIENT_SERVICE-URL_DEFAULT-ZONE=http://eureka-server:3000/eureka/
      - LOGGING_LEVEL_ROOT=INFO
    depends_on:
      - config-server
      - eureka-server

  enrich-server2:
    build:
      context: ../../enrich-service
      dockerfile: Dockerfile
    image: cloud-native-spring-batch/enrich-service
    hostname: enrich-service2
    container_name: enrich-service2
    ports:
      - "9891:9891"
    restart: always
    environment:
      - SERVER_PORT=9891
      - SPRING_CONFIG_IMPORT=configserver:http://config-server:8888/config-server
      - EUREKA_CLIENT_SERVICE-URL_DEFAULT-ZONE=http://eureka-server:3000/eureka/
      - LOGGING_LEVEL_ROOT=INFO
    depends_on:
      - config-server
      - eureka-server
```

---  

### batch-service

> application.yaml

```yaml
spring:
  application:
    name: batch-service
  config:
    import: "configserver:http://localhost:8888/config-server"
  cloud:
    loadbalancer:
      ribbon:
        enabled: false
  main:
    allow-bean-definition-overriding: true
  sql:
    init:
      mode: always
      platform: mysql
      schema-locations:
        - classpath:schema-mysql.sql
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:53306/spring_batch?useSSL=false&characterEncoding=UTF-8&serverTimezone=UTC
    username: root
    password: p@ssw0rd
    hikari:
      jdbc-url: jdbc:mysql://localhost:53306/spring_batch?useSSL=false&characterEncoding=UTF-8&serverTimezone=UTC
  batch:
    job:
      enabled: false
    jdbc:
      initialize-schema: always

eureka:
  client:
    service-url:
      defaultZone: http://localhost:3000/eureka/
    register-with-eureka: false
    fetch-registry: true

job:
  resource-path: s3://spring-batch/*.csv

cloud:
  aws:
    s3:
      endpoint: http://localhost:4572
      bucket: spring-batch
    region:
      static: ap-northeast-2
    stack:
      auto: false
#    credentials:
#      instanceProfile: true
```

<br />

> DownloadingJobExecutionListener(JobExecutionListener::beforeJob)

```java
@Slf4j
@Component
public class DownloadingJobExecutionListener extends JobExecutionListenerSupport {

    private final ResourcePatternResolver resourcePatternResolver;
    private final String path;

    @Autowired
    public DownloadingJobExecutionListener(ResourcePatternResolver resourcePatternResolver,
                                           AmazonS3Client amazonS3,
                                           @Value("${job.resource-path}") String path) {
        this.resourcePatternResolver = new PathMatchingSimpleStorageResourcePatternResolver(amazonS3,
                                                                                            resourcePatternResolver);
        this.path = path;
    }

    @Override
    public void beforeJob(JobExecution jobExecution) {
        try {
            final Resource[] resources = resourcePatternResolver.getResources(path);
            logger.info("found resources: {} in s3 path: {}", resources.length, path);

            final StringBuilder paths = new StringBuilder();
            for (Resource resource : resources) {
                File file = File.createTempFile("input", ".csv");

                StreamUtils.copy(resource.getInputStream(), new FileOutputStream(file));
                paths.append(file.getAbsolutePath()).append(",");

                logger.info("downloaded file: {}", file.getAbsolutePath());
            }

            final String localFiles = paths.substring(0, paths.length() - 1);
            logger.info("try to put localFiles {} to job execution context", localFiles);
            jobExecution.getExecutionContext().put("localFiles", localFiles);
        } catch (IOException e) {
            logger.error("Exception occur while executing beforeJob", e);
        }
    }
}
```

<br />

> ItemReader

```java
@Bean
@StepScope
public MultiResourceItemReader<Foo> reader(@Value("#{jobExecutionContext['localFiles']}") String paths)
        throws Exception {

    logger.info("[ItemReader] >> paths = {}", paths);
    MultiResourceItemReader<Foo> reader = new MultiResourceItemReader<>();

    reader.setName("multiReader");
    reader.setDelegate(delegate());

    final String[] parsedPaths = paths.split(",");
    logger.info("[ItemReader] parsed paths: {}", parsedPaths.length);
    final List<Resource> resources = new ArrayList<>(parsedPaths.length);

    for (String parsedPath : parsedPaths) {
        final Resource resource = new FileSystemResource(parsedPath);
        logger.info("[ItemReader] >> resource = {}", resource.getURI());
        resources.add(resource);
    }
    reader.setResources(resources.toArray(new Resource[resources.size()]));

    return reader;
}

@Bean
@StepScope
FlatFileItemReader<Foo> delegate() throws Exception {
    return new FlatFileItemReaderBuilder<Foo>()
            .name("fooReader")
            .delimited()
            .names("first", "scond", "third")
            .targetType(Foo.class)
            .build();
}
```

<br />

> Enrich Processor (Service to service call)

```yaml
@RequiredArgsConstructor
public class EnrichmentProcessor implements ItemProcessor<Foo, Foo> {

    private final RestTemplate restTemplate;

    @Recover
    public Foo fallback(Foo foo) {
        foo.setMessage("error-fallback");
        try {
            TimeUnit.MILLISECONDS.sleep(100L);
        } catch (Exception ignored) {
        }
        return foo;
    }

    @CircuitBreaker(maxAttempts = 1, resetTimeout = 1000L)
    @Override
    public Foo process(@NotNull Foo foo) {
        final ResponseEntity<String> responseEntity = restTemplate.exchange(
                "http://enrich-service/enrich",
                HttpMethod.GET,
                null,
                String.class
        );

        foo.setMessage(responseEntity.getBody());
        return foo;
    }
}
```

<br />

> docker-compose.yaml

```yaml
  batch-service:
    build:
      context: ../../batch-service
      dockerfile: Dockerfile
    image: cloud-native-spring-batch/batch-service
    hostname: batch-service
    container_name: batch-service
    ports:
      - "8899:8899"
    environment:
      - SPRING_CONFIG_IMPORT=configserver:http://config-server:8888/config-server
      - SPRING_PROFILES_ACTIVE=docker
    restart: always
    volumes:
      - ${HOME}/.aws:/root/.aws
    depends_on:
      - config-server
      - db
      - localstack
      - init.s3
```

---  

## 시작하기

아래와 같이 git clone을 받습니다.

```shell
$ git clone https://github.com/zacscoding/cloud-native-spring-batch
$ cd cloud-native-spring-batch
```

<br />

다음으로 **config-server**에서 이용할 git(local)을 초기화합니다.

```shell
$ cd tools/compose/config-repo
$ git init -b master
$ tree ./
./
├── batch-service-default.yaml    <-- batch-service 기본 설정 파일
├── batch-service-docker.yaml     <-- batch-service 도커 환경 설정 파일
└── enrich-service-default.yaml   <-- enrich-service 기본 설정 파일
$ git add .
$ git commit -m "initial commit"
$ git log
commit 0acfd11b8c0adf3049debb3464439779cef4584a (HEAD -> master)
...
```

다음으로 도커 이미지를 생성합니다.  
`compose.sh build`를 수행하면 `gradlew clean build`로 생성된 jar를 기반으로 도커 이미지를 생성합니다.  

```shell
$ cd ../../../
$ ./tools/script/compose.sh build

BUILD SUCCESSFUL in 5s
24 actionable tasks: 24 executed
db uses an image, skipping
localstack uses an image, skipping
init.s3 uses an image, skipping
Building config-server
...
```

아래 명령어를 통해 생성 된 도커 이미지를 확인할 수 있습니다.

```shell
$ docker images "cloud-native-spring-batch/*"
REPOSITORY                                 TAG       IMAGE ID       CREATED              SIZE
cloud-native-spring-batch/batch-service    latest    e5552191087e   About a minute ago   165MB  <-- batch service image. enabled service discovery but not register.
cloud-native-spring-batch/config-server    latest    b5aa388448cd   23 minutes ago       158MB  <-- config server image.
cloud-native-spring-batch/enrich-service   latest    297003bd5c9e   3 hours ago          147MB  <-- remote service image. register servie instance but not discovery.
cloud-native-spring-batch/eureka-server    latest    a693354fe9f2   3 hours ago          152MB  <-- eureka server image for service discovery.
```

위에서 생성된 도커 이미지를 실행합니다.

```shell
$ ./tools/script/compose.sh up
Creating network "compose_default" with the default driver
Creating config-server ...
Creating localstack    ...
Creating db            ...
...

$ docker ps -a
CONTAINER ID   IMAGE                                      COMMAND                  CREATED          STATUS          PORTS                                                                                                                NAMES
988af5e5dfa3   cloud-native-spring-batch/batch-service    "java org.springfram…"   16 seconds ago   Up 6 seconds    0.0.0.0:8899->8899/tcp, :::8899->8899/tcp                                                                            batch-service
da906356525f   cloud-native-spring-batch/enrich-service   "java org.springfram…"   18 seconds ago   Up 8 seconds    0.0.0.0:9890->9890/tcp, :::9890->9890/tcp                                                                            enrich-service1
b84f2db40063   cloud-native-spring-batch/enrich-service   "java org.springfram…"   18 seconds ago   Up 10 seconds   0.0.0.0:9891->9891/tcp, :::9891->9891/tcp                                                                            enrich-service2
7ac8437ae9ed   amazon/aws-cli                             "/bin/sh -c /s3/init…"   20 seconds ago   Up 15 seconds                                                                                                                        init.s3
899daa7d0909   cloud-native-spring-batch/eureka-server    "java org.springfram…"   25 seconds ago   Up 17 seconds   0.0.0.0:3000->3000/tcp, :::3000->3000/tcp                                                                            eureka-server
dbeecf4f005b   mysql:8.0.17                               "docker-entrypoint.s…"   29 seconds ago   Up 22 seconds   33060/tcp, 0.0.0.0:53306->3306/tcp, :::53306->3306/tcp                                                               db
325037126dc0   localstack/localstack:0.11.4               "docker-entrypoint.sh"   29 seconds ago   Up 19 seconds   4566-4571/tcp, 0.0.0.0:4572->4572/tcp, :::4572->4572/tcp, 4573-4597/tcp, 0.0.0.0:8080->8080/tcp, :::8080->8080/tcp   localstack
84ff343d694a   cloud-native-spring-batch/config-server    "java org.springfram…"   29 seconds ago   Up 24 seconds   0.0.0.0:8888->8888/tcp, :::8888->8888/tcp                                                                            config-server
7bc5eb40ef08   mysql:5.7                                  "docker-entrypoint.s…"   6 hours ago      Up 35 minutes   0.0.0.0:3306->3306/tcp, :::3306->3306/tcp, 33060/tcp                                                                 kas-th-api_db_1
```

**S3 확인하기**  

```shell
$ aws s3 ls "s3://spring-batch" --endpoint http://localhost:4572
2021-11-16 20:31:11       9131 data1.csv
2021-11-16 20:31:11       6570 data2.csv
```

**Config Server 확인하기**  

```shell
$ curl -XGET http://localhost:8888/config-server/batch-service/default | jq .
{
  "name": "batch-service",
  "profiles": [
    "default"
  ],
  "label": null,
  "version": "0acfd11b8c0adf3049debb3464439779cef4584a",
  "state": null,
  "propertySources": [
    {
      "name": "file:///config-server/config-repo/batch-service-default.yaml",
      "source": {
    }

$ curl -XGET http://localhost:8888/config-server/enrich-service/default | jq .
{
  "name": "enrich-service",
  "profiles": [
    "default"
  ],
  "label": n    
```

**Eureka Server 확인하기**

Eureka UI인 http://localhost:3000/eureka-ui 를 접속해서 확인할 수 있습니다.  

![Eureka Dashboard](https://user-images.githubusercontent.com/25560203/141978870-e77d9034-bdd3-4d54-8b17-560f2161d559.png)  

**Batch Service LoadBalancer 확인하기**  

아래와 같이 `enrich-service`의 instance id 값을 확인할 수 있습니다.

```shell
$ curl -XGET http://localhost:8899/debug/call
[enrich-service:016951d3ceb719d1a9a19cd1e321372d] Enrich_1
$ curl -XGET http://localhost:8899/debug/call
[enrich-service:df433be8124f5d045b2a1d153cba53a4] Enrich_1
$ curl -XGET http://localhost:8899/debug/call
[enrich-service:016951d3ceb719d1a9a19cd1e321372d] Enrich_2
```

**Batch Service ServiceDiscvoery 확인하기**  

```shell
$ curl -XGET http://localhost:8899/debug/discovery/services | jq .
{
  "enrich-service": [
    {
      "serviceId": "ENRICH-SERVICE",
      "secure": false,
      "uri": "http://enrich-service2:9891",
      "metadata": {
        "management.port": "9891"
      },
      "instanceId": "enrich-service:2682ed042c9168b213d7c72820b140f2",
      "instanceInfo": {
        "instanceId": "enrich-service:2682ed042c9168b213d7c72820b140f2",
        "app": "ENRICH-SERVICE",
        "appGroupName": null,
        "ipAddr": "172.18.0.7",
        "sid": "na",
        "homePageUrl": "http://enrich-service2:9891/",
        "statusPageUrl": "http://enrich-service2:9891/actuator/info",
        "healthCheckUrl": "http://enrich-service2:9891/actuator/health",
        "secureHealthCheckUrl": null,
        "vipAddress": "enrich-service",
        "secureVipAddress": "enrich-service",
        "countryId": 1,
        "dataCenterInfo": {
          "@class": "com.netflix.appinfo.InstanceInfo$DefaultDataCenterInfo",
          "name": "MyOwn"
        },
        "hostName": "enrich-service2",
        "status": "UP",
        "overriddenStatus": "UNKNOWN",
        "leaseInfo": {
          "renewalIntervalInSecs": 30,
          "durationInSecs": 90,
          "registrationTimestamp": 1637062291357,
          "lastRenewalTimestamp": 1637062948311,
          "evictionTimestamp": 0,
          "serviceUpTimestamp": 1637062291575
        },
        "isCoordinatingDiscoveryServer": false,
        "metadata": {
          "management.port": "9891"
        },
        "lastUpdatedTimestamp": 1637062291575,
        "lastDirtyTimestamp": 1637062288748,
        "actionType": "ADDED",
        "asgName": null
      },
      "scheme": "http",
      "host": "enrich-service2",
      "port": 9891
    },
    {
      "serviceId": "ENRICH-SERVICE",
      "secure": false,
      "uri": "http://enrich-service1:9890",
      "metadata": {
        "management.port": "9890"
      },
      "instanceId": "enrich-service:1177a6259f416d83c5853d68e77affd6",
      "instanceInfo": {
        "instanceId": "enrich-service:1177a6259f416d83c5853d68e77affd6",
        "app": "ENRICH-SERVICE",
        "appGroupName": null,
        "ipAddr": "172.18.0.8",
        "sid": "na",
        "homePageUrl": "http://enrich-service1:9890/",
        "statusPageUrl": "http://enrich-service1:9890/actuator/info",
        "healthCheckUrl": "http://enrich-service1:9890/actuator/health",
        "secureHealthCheckUrl": null,
        "vipAddress": "enrich-service",
        "secureVipAddress": "enrich-service",
        "countryId": 1,
        "dataCenterInfo": {
          "@class": "com.netflix.appinfo.InstanceInfo$DefaultDataCenterInfo",
          "name": "MyOwn"
        },
        "hostName": "enrich-service1",
        "status": "UP",
        "overriddenStatus": "UNKNOWN",
        "leaseInfo": {
          "renewalIntervalInSecs": 30,
          "durationInSecs": 90,
          "registrationTimestamp": 1637062291352,
          "lastRenewalTimestamp": 1637062948314,
          "evictionTimestamp": 0,
          "serviceUpTimestamp": 1637062291572
        },
        "isCoordinatingDiscoveryServer": false,
        "metadata": {
          "management.port": "9890"
        },
        "lastUpdatedTimestamp": 1637062291573,
        "lastDirtyTimestamp": 1637062288803,
        "actionType": "ADDED",
        "asgName": null
      },
      "scheme": "http",
      "host": "enrich-service1",
      "port": 9890
    }
  ]
}
```


**Batch Service Job 실행하기**

```shell
$ curl -XPOST http://localhost:8899/batch/s3jdbcJob
{"exitCode":"COMPLETED","exitDescription":"","running":false}
```

아래와 같이 DB 값을 확인할 수 있습니다.

````shell
$ docker exec -it db /bin/bash
root@db:/# mysql -u root -p
mysql> use spring_batch
mysql> SELECT * FROM foo LIMIT 50;
+--------+-------+--------------------+---------------------------------+-------------------------------------------------------------+
| foo_id | first | second             | third                           | message                                                     |
+--------+-------+--------------------+---------------------------------+-------------------------------------------------------------+
|      1 | data1 | Annamae Luettgen   | isobelfahey@hauck.net           | [enrich-service:016951d3ceb719d1a9a19cd1e321372d] Enrich_3  |
|      2 | data1 | Ivy Williamson     | murielblick@langworth.name      | [enrich-service:df433be8124f5d045b2a1d153cba53a4] Enrich_2  |
|      3 | data1 | Euna Cremin        | carolinarohan@grady.com         | [enrich-service:016951d3ceb719d1a9a19cd1e321372d] Enrich_4  |
|      4 | data1 | Trevion Rogahn     | marcelinolockman@kozey.net      | [enrich-service:df433be8124f5d045b2a1d153cba53a4] Enrich_3  |
|      5 | data1 | Kaleb Blick        | pansyaltenwerth@goyette.io      | [enrich-service:016951d3ceb719d1a9a19cd1e321372d] Enrich_5  |
|      6 | data1 | Crystel Bayer      | arliebins@simonis.com           | [enrich-service:df433be8124f5d045b2a1d153cba53a4] Enrich_4  |
|      7 | data1 | Rosanna West       | odieruecker@prosacco.net        | [enrich-service:016951d3ceb719d1a9a19cd1e321372d] Enrich_6  |
|      8 | data1 | Armani Leffler     | gilbertbrakus@schaden.name      | [enrich-service:df433be8124f5d045b2a1d153cba53a4] Enrich_5  |
|      9 | data1 | Gail Huels         | fosterbaumbach@cummings.name    | [enrich-service:016951d3ceb719d1a9a19cd1e321372d] Enrich_7  |
|     10 | data1 | Miller Durgan      | hymanhayes@labadie.org          | [enrich-service:df433be8124f5d045b2a1d153cba53a4] Enrich_6  |
|     11 | data1 | Daija Gulgowski    | vickybalistreri@kirlin.name     | [enrich-service:016951d3ceb719d1a9a19cd1e321372d] Enrich_8  |
|     12 | data1 | Susan Hahn         | chasejast@cremin.com            | [enrich-service:df433be8124f5d045b2a1d153cba53a4] Enrich_7  |
|     13 | data1 | Marcos Bayer       | marysewuckert@ullrich.org       | [enrich-service:016951d3ceb719d1a9a19cd1e321372d] Enrich_9  |
|     14 | data1 | Ashley Kuvalis     | kamerongutmann@mohr.org         | [enrich-service:df433be8124f5d045b2a1d153cba53a4] Enrich_8  |
|     15 | data1 | Christiana Sanford | emelieshanahan@smith.info       | error-fallback                                              |
|     16 | data1 | Irwin Morissette   | darrellrice@kiehn.biz           | error-fallback                                              |
|     17 | data1 | Tyshawn Volkman    | demarcuswyman@daugherty.info    | error-fallback                                              |
|     18 | data1 | Marianne Kuphal    | daynekoss@bauch.name            | error-fallback                                              |
|     19 | data1 | Myrl Johnston      | careyfritsch@rolfson.io         | error-fallback                                              |
|     20 | data1 | Megane Reichert    | esmeraldamuller@simonis.name    | error-fallback                                              |
|     21 | data1 | Lea Torp           | danrosenbaum@cole.net           | [enrich-service:df433be8124f5d045b2a1d153cba53a4] Enrich_9  |
|     22 | data1 | Lessie Ankunding   | quincyleuschke@west.name        | [enrich-service:016951d3ceb719d1a9a19cd1e321372d] Enrich_11 |
|     23 | data1 | Issac Brakus       | bartonrodriguez@homenick.com    | error-fallback                                              |
|     24 | data1 | Jerome Ernser      | clemmiemills@bergstrom.com      | error-fallback                                              |
|     25 | data1 | Raymundo Schmidt   | mohamedbrakus@hauck.info        | error-fallback                                              |
|     26 | data1 | Tito Windler       | tamiakub@mckenzie.com           | error-fallback                                              |
|     27 | data1 | Annabelle Witting  | newellkling@braun.com           | error-fallback                                              |
|     28 | data1 | Odessa Schaefer    | kylehyatt@nader.info            | error-fallback                                              |
|     29 | data1 | Josefa Leuschke    | vernievon@torp.io               | error-fallback                                              |
|     30 | data1 | Vesta Rempel       | jesusbogan@cummings.net         | error-fallback                                              |
|     31 | data1 | Eladio Roob        | jaidenwilderman@nicolas.biz     | error-fallback                                              |
|     32 | data1 | Dawn Zulauf        | pinkieharvey@nicolas.com        | error-fallback                                              |
|     33 | data1 | Damaris Kirlin     | chaseferry@lang.net             | [enrich-service:016951d3ceb719d1a9a19cd1e321372d] Enrich_12 |
|     34 | data1 | Sven Moore         | lysannegislason@corkery.net     | [enrich-service:df433be8124f5d045b2a1d153cba53a4] Enrich_11 |
````

위와 같이 `enrich-service`의 Count 값을 기준으로 Circuit Open 후 Fallback 메소드가 실행된것을 확인할 수 있습니다.

---  

# References

- https://cloud.spring.io/spring-cloud-config/reference/html/
- https://circlee7.medium.com/spring-cloud-config-%EC%A0%95%EB%A6%AC-1-%EB%B2%88%EC%99%B8-da81585400fa
- https://docs.spring.io/spring-cloud-netflix/docs/2.2.4.RELEASE/reference/html/
- https://medium.com/swlh/spring-cloud-high-availability-for-eureka-b5b7abcefb32
- https://github.com/Netflix/eureka/wiki/Understanding-Eureka-Peer-to-Peer-Communication
- https://coe.gitbook.io/guide/service-discovery/eureka