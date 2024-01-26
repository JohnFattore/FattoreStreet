from django.test import TestCase

# Create your tests here.
from rest_framework.test import APITestCase
from rest_framework import status
from django.contrib.auth.models import User
from portfolio.models import Asset
from portfolio.serializers import AssetSerializer 


class AssetRetrieveDestroyViewTests(APITestCase):
    def setUp(self):
        # Create a user for testing purposes
        self.user = User.objects.create_user(username='testuser', password='testpassword')

        # Create an asset owned by the user
        self.asset = Asset.objects.create(ticker='VTI', 
                                          shares=10, 
                                          costbasis=200, 
                                          buy='2023-10-13', 
                                          user=self.user)

        # Set up authentication for the client
        # self.client.force_authenticate(user=user, token=user.auth_token)

    def test_retrieve_asset(self):
        url = f'/api/asset/{self.asset.id}/'
        response = self.client.get(url)
        print("The URL is:" + self.asset.buy)
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(response.data, AssetSerializer(self.asset).data)

    def test_delete_asset(self):
        url = f'/api/asset/{self.asset.id}/'
        response = self.client.delete(url)

        self.assertEqual(response.status_code, status.HTTP_204_NO_CONTENT)
        self.assertFalse(Asset.objects.filter(id=self.asset.id).exists())