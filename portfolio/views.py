# only API views, see retiredViews for old django frontend views
from django.contrib.auth.models import User
from django.db import models
# API modules using drf
from rest_framework import viewsets, permissions
from rest_framework.parsers import JSONParser
from .serializers import AssetSerializer, UserSerializer
from .models import Asset
from django.utils.decorators import method_decorator
from django.views.decorators.csrf import csrf_exempt
from django.http import HttpResponse, JsonResponse

# API endpoint that allows users to be viewed or edited.
class UserViewSet(viewsets.ModelViewSet):
    queryset = User.objects.all()
    serializer_class = UserSerializer
    #permission_classes = [permissions.IsAuthenticated]

# API endpoint that allows users to be viewed or edited.
# @method_decorator(csrf_exempt, name='dispatch')
class AssetViewSet(viewsets.ModelViewSet):
    queryset = Asset.objects.all()
    serializer_class = AssetSerializer
    permission_classes = [permissions.IsAuthenticated]

# List all assets, or create a new asset.
@csrf_exempt
def asset_list(request):
    # 
    if request.method == 'GET':
        user_key = request.GET.get("user_key")
        assets = Asset.objects.all().filter(user_key = user_key)
        serializer = AssetSerializer(assets, many=True)
        return JsonResponse(serializer.data, safe=False)

    elif request.method == 'POST':
        data = JSONParser().parse(request)
        serializer = AssetSerializer(data=data)
        if serializer.is_valid():
            serializer.save()
            return JsonResponse(serializer.data, status=201)
        print(serializer.errors)
        return JsonResponse(serializer.errors, status=400)