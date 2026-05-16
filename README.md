# WildFly Kafka queues demo

Local demo for running a WildFly application on Colima Kubernetes with Kafka managed through ArgoCD.

This repository intentionally keeps infrastructure manifests and sample application code together. That is not the usual production split, but it makes the demo easy to follow.

## Layout

```text
argocd/applications/      ArgoCD Application manifests
platform/kafka/           Single-node Kafka demo deployment
apps/wildfly-kafka/       WildFly Kubernetes manifests
sample-app/               Jakarta EE / WildFly Kafka sample app
bootstrap/                One-time local bootstrap notes
```

## Bootstrap

Install ArgoCD once into a clean Colima Kubernetes cluster:

```bash
kubectl create namespace argocd
kubectl apply --server-side --force-conflicts -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml
kubectl -n argocd rollout status deploy/argocd-server
```

Push this repository first, then apply the root ArgoCD application:

```bash
kubectl apply -f argocd/root.yaml
```

Build the local WildFly image into Colima's Docker runtime:

```bash
docker build -t wildfly-kafka-sample:dev sample-app
```

Then sync the WildFly app in ArgoCD, or wait for automatic sync.

## Try it

```bash
kubectl -n jbossqueues port-forward svc/wildfly-kafka 8080:8080
curl -X POST --data 'hello from wildfly' http://localhost:8080/api/messages
curl http://localhost:8080/api/messages/last
kubectl -n jbossqueues logs deploy/wildfly-kafka -f
```
