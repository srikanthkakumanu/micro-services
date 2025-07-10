It is a collection of all microservice modules.

To run the Docker compose successfully, Git clone all microservices in this directory.

### List of Microservices

- user-service
- eureka-discovery (service discovery)
- api-gateway (API gateway & Load Balancer)
- todo-service
- video-service
- books-service

## Cloning Micro-service Repositories

To clone all micro-service repositories, you can use the following script.

```bash
sh ./clone_repos.sh
```

## Building Micro-service Jars & Docker Images

To build Jars for micro services and building docker images, you can use the following script.

Note: You comment the unwanted micro service in this script to omit them from the execution.

```bash
sh ./buld.sh
```

## Running micro-services and & DB

You can use the following command: ```bash docker compose up -d```. It runs the microservices in detached mode.

## Redirect all console logs to file

We can redirect all the console logs to a specific file by using the following command.

```bash

docker compose logs -f -t > console_log.log
```


## Setting Up Vault for Secret Management


1. [https://myros.net/hashicorp-vault-docker-compose-part1](https://https://myros.net/hashicorp-vault-docker-compose-part1)
2. https://myros.net/hashicorp-vault-docker-compose-part2
