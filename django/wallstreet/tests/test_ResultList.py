from rest_framework.test import APITestCase
from django.contrib.auth.models import User
from wallstreet.models import Selection, Option, Result
from wallstreet.helperFunctions import getSunday
from rest_framework.test import APIRequestFactory, force_authenticate
from rest_framework.test import APIClient
from django.urls import reverse
from rest_framework import status
from wallstreet.views import ResultListAPI

class ResultUnitTest(APITestCase):
    def setUp(self):
        # results should just be manually made, celery tasks tested in celery.py
        self.sunday = getSunday(0)
        self.user = User.objects.create_user(username='testuser', password='testpassword')
        self.user2 = User.objects.create_user(username='testuser2', password='testpassword2')
        self.result = Result.objects.create(portfolioPercentChange=0.53, sunday=self.sunday, user=self.user)
        self.result2 = Result.objects.create(portfolioPercentChange=0.76, sunday=self.sunday, user=self.user2)
        self.factory = APIRequestFactory()
        self.client = APIClient()
        self.url = reverse('resultList')
        # self.url = '/wallstreet/api/results/'
        self.view = ResultListAPI.as_view()

    def test_list_results(self):
        request = self.factory.get(self.url)
        force_authenticate(request, user=self.user)
        response = self.view(request)
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(len(response.data), 1)
        self.assertEqual(response.data[0]['portfolioPercentChange'], str(self.result.portfolioPercentChange))

    def test_list_results_unauthorized(self):
        data = { 'sunday': self.sunday }
        request = self.factory.get(self.url, data)
        #force_authenticate(request, user=self.user)
        response = self.view(request)
        self.assertEqual(response.status_code, status.HTTP_401_UNAUTHORIZED)

    def test_list_results_client(self):
        data = {'sunday': self.sunday}
        self.client.force_authenticate(user=self.user)
        response = self.client.get(self.url, data)
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(len(response.data), 1)
        self.assertEqual(response.data[0]['portfolioPercentChange'], str(self.result.portfolioPercentChange))