from rest_framework.test import APITestCase
from django.contrib.auth.models import User
from wallstreet.models import Selection, Option, Result
from wallstreet.helperFunctions import getSunday
from rest_framework.test import APIRequestFactory, force_authenticate
from rest_framework.test import APIClient
from django.urls import reverse
from rest_framework import status
from wallstreet.views import ResultRetrieveAPI

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
        self.url = reverse('resultRetrieve', kwargs={'pk': self.result.id})
        self.view = ResultRetrieveAPI.as_view()

    def test_retrieve_result(self):
        request = self.factory.get(self.url, format='json')
        force_authenticate(request, user=self.user)
        response = self.view(request, pk=self.result.id)
        response.render()
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(self.result.id, response.data['id'])

    def test_retrieve_result_unauthorized(self):
        request = self.factory.get(self.url, format='json')
        #force_authenticate(request, user=self.user)
        response = self.view(request, pk=self.result.id)
        response.render()
        self.assertEqual(response.status_code, status.HTTP_401_UNAUTHORIZED)

    def test_retrieve_result_wrong_user(self):
        request = self.factory.get(self.url, format='json')
        force_authenticate(request, user=self.user2)
        response = self.view(request, pk=self.result.id)
        response.render()
        self.assertEqual(response.status_code, status.HTTP_403_FORBIDDEN)

    def test_retrieve_result_client(self):
        self.client.force_authenticate(user=self.user)
        response = self.client.get(self.url)
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(self.result.id, response.data['id'])