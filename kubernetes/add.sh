#!/bin/sh
kubectl apply -f nginx-deployment.yaml
kubectl apply -f django-deployment.yaml
kubectl apply -f nginx-service.yaml
kubectl apply -f django-service.yaml