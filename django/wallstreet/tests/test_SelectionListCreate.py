from rest_framework.test import APITestCase
from django.contrib.auth.models import User
from rest_framework import status
from wallstreet.models import Selection, Option
from wallstreet.views import SelectionListCreateAPI
from rest_framework.test import APIRequestFactory, force_authenticate
from rest_framework.test import APIClient
from django.urls import reverse
from wallstreet.helperFunctions import getSunday

class SelectionUnitTest(APITestCase):
    def setUp(self):
        self.user = User.objects.create_user(username='testuser', password='testpassword')
        self.user2 = User.objects.create_user(username='testuser2', password='testpassword')
        self.lastSunday = getSunday(0)
        self.nextSunday = getSunday(1)
        self.option = Option.objects.create(ticker="V", sunday=self.nextSunday, benchmark=False)
        self.option2 = Option.objects.create(ticker="F", sunday=self.nextSunday, benchmark=False)
        self.option3 = Option.objects.create(ticker="AAPL", sunday=self.nextSunday, benchmark=False)
        self.option4 = Option.objects.create(ticker="MSFT", sunday=self.nextSunday, benchmark=False)
        self.option5 = Option.objects.create(ticker="LLY", sunday=self.lastSunday, benchmark=False)
        self.option6 = Option.objects.create(ticker="SPY", sunday=self.nextSunday, benchmark=True)
        self.option7 = Option.objects.create(ticker="M", sunday=self.lastSunday, benchmark=False)
        Selection.objects.create(option=self.option2, user=self.user)
        Selection.objects.create(option=self.option3, user=self.user)
        Selection.objects.create(option=self.option7, user=self.user)
        Selection.objects.create(option=self.option, user=self.user2)
        Selection.objects.create(option=self.option2, user=self.user2)
        Selection.objects.create(option=self.option3, user=self.user2)
        self.factory = APIRequestFactory()
        self.client = APIClient()
        self.url = reverse('selectionListCreate')
        # self.url = '/wallstreet/api/selections/'
        self.view = SelectionListCreateAPI.as_view()

    def test_create_selection(self):
        data = {'option': self.option.id }
        request = self.factory.post(self.url, data, format='json')
        force_authenticate(request, user=self.user)
        response = self.view(request)
        response.render()
        self.assertEqual(response.status_code, status.HTTP_201_CREATED)

    def test_create_selection_duplicate_option(self):
        data = {'option': self.option.id}
        requestPre = self.factory.post(self.url, data, format='json')
        force_authenticate(requestPre, user=self.user)
        self.view(requestPre)
        # submit same request again
        request = self.factory.post(self.url, data, format='json')
        force_authenticate(request, user=self.user)
        response = self.view(request)
        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)

    def test_create_selection_invalid_option(self):
        data = {'option': -1}
        request = self.factory.post(self.url, data, format='json')
        force_authenticate(request, user=self.user)
        response = self.view(request)
        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)
        response.render()
        self.assertIn("invalid", str(response.content).lower())

    # only 3 per week!
    def test_create_selection_too_many_options(self):
        data = {'option': self.option.id}
        request = self.factory.post(self.url, data, format='json')
        force_authenticate(request, user=self.user)
        response = self.view(request)
        response.render()
        self.assertEqual(response.status_code, status.HTTP_201_CREATED)
        data = {'option': self.option4.id}
        request = self.factory.post(self.url, data, format='json')
        force_authenticate(request, user=self.user)
        response = self.view(request)
        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)
        response.render()
        self.assertIn("3", str(response.content).lower())

    def test_create_selection_past_sunday(self):
        data = {'option': self.option5.id}
        request = self.factory.post(self.url, data, format='json')
        force_authenticate(request, user=self.user)
        response = self.view(request)
        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)
        response.render()
        self.assertIn("past", str(response.content).lower())

    def test_create_selection_benchmark(self):
        data = {'option': self.option6.id}
        request = self.factory.post(self.url, data, format='json')
        force_authenticate(request, user=self.user)
        response = self.view(request)
        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)
        response.render()
        self.assertIn("benchmark", str(response.content).lower())

    def test_create_selection_unauthorized(self):
        data = {'option': self.option.id }
        request = self.factory.post(self.url, data, format='json')
        #force_authenticate(request, user=self.user)
        response = self.view(request)
        response.render()
        self.assertEqual(response.status_code, status.HTTP_401_UNAUTHORIZED)

    def test_create_selection_client(self):
        data = {'option': self.option.id }
        self.client.force_authenticate(user=self.user)
        response = self.client.post(self.url, data)
        self.assertEqual(response.status_code, status.HTTP_201_CREATED)

#####################################################################################
    # get requests

    # expect no selections returned if sunday is not specified
    def test_list_selections_no_params(self):
        data = { }
        request = self.factory.get(self.url, data)
        force_authenticate(request, user=self.user)
        response = self.view(request)
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(len(response.data), 0)

    def test_list_selections_sunday(self):
        data = {'sunday': self.nextSunday}
        request = self.factory.get(self.url, data)
        force_authenticate(request, user=self.user)
        response = self.view(request)
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(len(response.data), 2)

    def test_list_selections_unauthorized(self):
        data = {'sunday': self.nextSunday}
        request = self.factory.get(self.url, data)
        #force_authenticate(request, user=self.user)
        response = self.view(request)
        self.assertEqual(response.status_code, status.HTTP_401_UNAUTHORIZED)
        response.render()
        self.assertIn("authentication", str(response.content).lower())

    def test_list_selections_client(self):
        data = {'sunday': self.nextSunday}
        self.client.force_authenticate(user=self.user)
        response = self.client.get(self.url, data)
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(len(response.data), 2)

    def test_list_selections_client_unauthorized(self):
        data = {'sunday': self.nextSunday}
        #self.client.force_authenticate(user=self.user)
        response = self.client.get(self.url, data)
        self.assertEqual(response.status_code, status.HTTP_401_UNAUTHORIZED)
        response.render()
        self.assertIn("authentication", str(response.content).lower())