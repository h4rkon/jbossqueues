# Fresh Colima and ArgoCD bootstrap

Reset Colima:

```bash
colima delete --force
colima start --kubernetes
kubectl get nodes
```

Install ArgoCD:

```bash
kubectl create namespace argocd
kubectl apply --server-side --force-conflicts -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml
kubectl -n argocd rollout status deploy/argocd-server
```

Access the UI:

```bash
kubectl -n argocd port-forward svc/argocd-server 8081:443
kubectl -n argocd get secret argocd-initial-admin-secret \
  -o jsonpath='{.data.password}' | base64 -d; echo
```

After the repository content is pushed to GitHub, apply the root application:

```bash
kubectl apply -f argocd/root.yaml
```

If the GitHub repository is private, configure ArgoCD repository credentials before applying the applications.
