from rest_framework.test import APITestCase
from django.contrib.auth.models import User
from rest_framework import status
from portfolio.models import Asset  # Import your Asset model
from portfolio.views import AssetListCreateView
from rest_framework.test import APIRequestFactory, force_authenticate
from rest_framework.test import APIClient

# should be using self.client instead pf the API RequestFactory, however the client doesnt work for me
class AssetListCreateViewTest(APITestCase):
    def setUp(self):
        # Create a user for authentication
        self.user = User.objects.create_user(username='testuser', password='testpassword')

    def test_create_asset(self):
        factory = APIRequestFactory()
        view = AssetListCreateView.as_view() 
        url = '/api/assets/'
        data = {'ticker': 'VTI',                
                'shares': 10, 
                'costbasis': 200, 
                'buy': '2023-10-13',
                'user': self.user.id}
        request = factory.post(url, data, format='json')

        force_authenticate(request, user=self.user)
        response = view(request)

        self.assertEqual(response.status_code, status.HTTP_201_CREATED)
        created_asset = Asset.objects.latest('id')
        self.assertEqual(created_asset.user, self.user)

    def test_create_asset_negative_shares(self):
        factory = APIRequestFactory()
        view = AssetListCreateView.as_view() 
        url = '/api/assets/'
        data = {'ticker': 'VTI',                
                'shares': -10, 
                'costbasis': 200, 
                'buy': '2023-10-13',
                'user': self.user.id}
        request = factory.post(url, data, format='json')

        force_authenticate(request, user=self.user)
        response = view(request)

        self.assertEqual(response.status_code, status.HTTP_201_CREATED)
        created_asset = Asset.objects.latest('id')
        self.assertEqual(created_asset.user, self.user)


    def test_create_asset_negative_costBasis(self):
        factory = APIRequestFactory()
        view = AssetListCreateView.as_view() 
        url = '/api/assets/'
        data = {'ticker': 'VTI',                
                'shares': 10, 
                'costbasis': -200, 
                'buy': '2023-10-13',
                'user': self.user.id}
        request = factory.post(url, data, format='json')

        force_authenticate(request, user=self.user)
        response = view(request)

        self.assertEqual(response.status_code, status.HTTP_201_CREATED)
        created_asset = Asset.objects.latest('id')
        self.assertEqual(created_asset.user, self.user)

#    def test_create_asset_client(self):
#        self.client.force_authenticate(user=self.user)
#        # client.login(username='testuser', password='testpassword')
#        url = '/api/assets/'
#        data = {'ticker': 'VTI',                
#                'shares': 10, 
#                'costbasis': 200, 
#                'buy': '2023-10-13',
#                'user': self.user.id}
#
#        response = self.client.post(url, data, format='json')
#        print("Response Content:", response.content)
#        self.assertEqual(response.status_code, status.HTTP_201_CREATED)
#        #created_asset = Asset.objects.latest('id')
#        #self.assertEqual(created_asset.user, self.user)