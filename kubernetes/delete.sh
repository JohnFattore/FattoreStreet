#!/bin/sh
kubectl delete -n default service nginx-service
kubectl delete -n default service django-service
kubectl delete -n default deployment nginx-deployment
kubectl delete -n default deployment django-deployment