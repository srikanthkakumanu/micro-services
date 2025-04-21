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

```bash
sh ./buld.sh
```

## Running micro-services and & DB

You can use the following command: ```bash docker compose up -d```
