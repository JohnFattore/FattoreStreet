from rest_framework.test import APITestCase
from django.contrib.auth.models import User
from rest_framework import status
from wallstreet.models import Selection, Option
from wallstreet.views import SelectionRetrieveDestroyAPI
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
        self.selection = Selection.objects.create(option=self.option, user=self.user)
        self.selection2 = Selection.objects.create(option=self.option, user=self.user2)
        self.factory = APIRequestFactory()
        self.client = APIClient()
        self.url = reverse('selectionRetrieveDestroy', kwargs={'pk': self.selection.id})
        # self.url = '/wallstreet/api/selections/'
        self.view = SelectionRetrieveDestroyAPI.as_view()

    def test_retrieve_selection(self):
        request = self.factory.get(self.url, format='json')
        force_authenticate(request, user=self.user)
        response = self.view(request, pk=self.selection.id)
        response.render()
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(self.selection.option.id, response.data['id'])

    def test_retrieve_selection_unauthorized(self):
        request = self.factory.get(self.url, format='json')
        # force_authenticate(request, user=self.user)
        response = self.view(request, pk=self.selection.id)
        response.render()
        self.assertEqual(response.status_code, status.HTTP_401_UNAUTHORIZED)

    def test_retrieve_selection_wrong_user(self):
        request = self.factory.get(self.url, format='json')
        force_authenticate(request, user=self.user2)
        response = self.view(request, pk=self.selection.id)
        response.render()
        self.assertEqual(response.status_code, status.HTTP_403_FORBIDDEN)

    def test_retrieve_selection_client(self):
        self.client.force_authenticate(user=self.user)
        response = self.client.get(self.url)
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(self.selection.option.id, response.data['id'])

###############################################################################################
        # Delete

    def test_delete_selection(self):
        request = self.factory.delete(self.url, format='json')
        force_authenticate(request, user=self.user)
        response = self.view(request, pk=self.selection.id)
        response.render()
        self.assertEqual(response.status_code, status.HTTP_204_NO_CONTENT)

    def test_delete_selection_unauthorized(self):
        request = self.factory.delete(self.url, format='json')
        # force_authenticate(request, user=self.user)
        response = self.view(request, pk=self.selection.id)
        response.render()
        self.assertEqual(response.status_code, status.HTTP_401_UNAUTHORIZED)

    def test_delete_selection_wrong_user(self):
        request = self.factory.delete(self.url, format='json')
        force_authenticate(request, user=self.user2)
        response = self.view(request, pk=self.selection.id)
        response.render()
        self.assertEqual(response.status_code, status.HTTP_403_FORBIDDEN)

    def test_delete_selection_client(self):
        self.client.force_authenticate(user=self.user)
        response = self.client.delete(self.url)
        self.assertEqual(response.status_code, status.HTTP_204_NO_CONTENT)