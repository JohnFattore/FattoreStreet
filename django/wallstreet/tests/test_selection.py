from rest_framework.test import APITestCase
from django.contrib.auth.models import User
from rest_framework import status
from wallstreet.models import Selection, Option
from wallstreet.views import SelectionListCreateView
from rest_framework.test import APIRequestFactory, force_authenticate
from rest_framework.test import APIClient
from django.urls import reverse

class SelectionCreateTest(APITestCase):
    def setUp(self):
        # Create a user for authentication
        self.user = User.objects.create_user(username='testuser', password='testpassword')
        # self.selection = Selection.objects.create(option=1, sunday='2024-03-03', user=self.user.id)
        self.option = Option.objects.create(ticker="SPY", sunday='2024-03-03')
        self.option2 = Option.objects.create(ticker="VTI", sunday='2024-03-03')
        self.option3 = Option.objects.create(ticker="AAPL", sunday='2024-03-03')
        self.option4 = Option.objects.create(ticker="MSFT", sunday='2024-03-03')
        Selection.objects.create(option=self.option2, user=self.user)
        Selection.objects.create(option=self.option3, user=self.user)
        self.factory = APIRequestFactory()
        self.client = APIClient()
        self.url = reverse('selections')
        # self.url = '/wallstreet/api/selections/'
        self.view = SelectionListCreateView.as_view()

    def test_create_selection(self):
        data = {'option': self.option.id, 'user': self.user.id}
        request = self.factory.post(self.url, data, format='json')
        force_authenticate(request, user=self.user)
        response = self.view(request)
        response.render()
        self.assertEqual(response.status_code, status.HTTP_201_CREATED)

    def test_create_selection_duplicate_option(self):
        data = {'option': self.option.id, 'user': self.user.id}
        requestPre = self.factory.post(self.url, data, format='json')
        force_authenticate(requestPre, user=self.user)
        responsePre = self.view(requestPre)
        # submit same request again
        request = self.factory.post(self.url, data, format='json')
        force_authenticate(request, user=self.user)
        response = self.view(request)
        # print("response: " + str(response.content))
        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)

    def test_create_selection_invalid_option(self):
        data = {'option': -1, 'user': self.user.id}
        request = self.factory.post(self.url, data, format='json')
        force_authenticate(request, user=self.user)
        response = self.view(request)
        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)
        response.render()
        self.assertIn("invalid", str(response.content).lower())

    # only 3 per week!
    #def test_create_selection_too_many_options(self):
    #    data = {'option': 4, 'user': self.user.id}
    #    request = self.factory.post(self.url, data, format='json')
    #    force_authenticate(request, user=self.user)
    #    response = self.view(request)
    #    self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)
    #    response.render()
    #    self.assertIn("3", str(response.content).lower())

    def test_list_selections(self):
        request = self.factory.get(self.url)
        force_authenticate(request, user=self.user)
        response = self.view(request)
        self.assertEqual(response.status_code, status.HTTP_200_OK)

    def test_list_selections_client(self):
        self.client.force_authenticate(user=self.user)
        response = self.client.get(self.url)
        self.assertEqual(response.status_code, status.HTTP_200_OK)