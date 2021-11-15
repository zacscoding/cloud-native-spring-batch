# Cloud Native Spring Batch

## Table Of Contents

- [#예제 구성](#예제-구성)
- [#시작 하기](#시작하기)

---  

## 예제 구성
; TBD

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
```

다음으로 도커 이미지를 생성합니다.  

```shell
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

