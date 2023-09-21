# Docker builds

docker buildx build --push -t "atlasoflivingaustralia/lists-service:$(git rev-parse --short HEAD)" -f src/main/docker/Dockerfile .