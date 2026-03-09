# Kubernetes Manifests

This directory contains starter Kubernetes manifests for core platform services.

## Layout

- `base/`: namespace, shared config map, and core service workloads.

## Apply

```bash
kubectl apply -k k8s/base
```

## Notes

- Replace image tags in each deployment before applying to a cluster.
- These manifests assume external or separately managed infra for:
  - PostgreSQL
  - Kafka
  - Elasticsearch
  - Redis
- Health probes use `/actuator/health`.
