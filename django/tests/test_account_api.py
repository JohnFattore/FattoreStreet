from django.urls import reverse
from rest_framework import status
from django.contrib.auth.models import User
from portfolio.models import Account
from tests.base import BaseAPITestCase


class AccountAPITests(BaseAPITestCase):
    def setUp(self):
        super().setUp()
        self.authenticate_client()
        self.account_data = {'name': 'Test Account', 'account_type': 'TRADITIONAL_IRA'}
        self.response = self.client.post(reverse('accounts'), self.account_data, format='json')

    def test_create_account(self):
        """Test creating a new account."""
        self.assertEqual(self.response.status_code, status.HTTP_201_CREATED)
        self.assertEqual(Account.objects.count(), 1)
        self.assertEqual(Account.objects.get().name, 'Test Account')
        self.assertEqual(Account.objects.get().account_type, 'TRADITIONAL_IRA')

    def test_get_accounts(self):
        """Test retrieving list of accounts."""
        response = self.client.get(reverse('accounts'))
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(len(response.data), 1)

    def test_get_account_detail(self):
        """Test retrieving a specific account."""
        account = Account.objects.get()
        response = self.client.get(reverse('account', args=[account.id]))
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(response.data['name'], 'Test Account')

    def test_update_account(self):
        """Test updating an account."""
        account = Account.objects.get()
        new_data = {'name': 'Updated Account'}
        response = self.client.put(reverse('account', args=[account.id]), new_data, format='json')
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(Account.objects.get().name, 'Updated Account')

    def test_delete_account(self):
        """Test deleting an account."""
        account = Account.objects.get()
        response = self.client.delete(reverse('account', args=[account.id]))
        self.assertEqual(response.status_code, status.HTTP_204_NO_CONTENT)
        self.assertEqual(Account.objects.count(), 0)

    def test_permissions(self):
        """Test that user cannot see other users' accounts."""
        other_user = User.objects.create_user(username='otheruser', password='otherpassword')
        Account.objects.create(name='Other Account', user=other_user)

        response = self.client.get(reverse('accounts'))
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(len(response.data), 1)
        self.assertEqual(response.data[0]['name'], 'Test Account')

    def test_access_other_users_account(self):
        """Test that user cannot access another user's account detail."""
        other_user = User.objects.create_user(username='otheruser', password='otherpassword')
        other_account = Account.objects.create(name='Other Account', user=other_user)
        response = self.client.get(reverse('account', args=[other_account.id]))
        self.assertEqual(response.status_code, status.HTTP_404_NOT_FOUND)

    def test_create_account_unauthenticated(self):
        """Test that unauthenticated user cannot create an account."""
        self.unauthenticate_client()
        data = {'name': 'Unauthorized Account', 'account_type': 'ROTH_IRA'}
        response = self.client.post(reverse('accounts'), data, format='json')
        self.assertEqual(response.status_code, status.HTTP_401_UNAUTHORIZED)

    def test_list_accounts_unauthenticated(self):
        """Test that unauthenticated user cannot list accounts."""
        self.unauthenticate_client()
        response = self.client.get(reverse('accounts'))
        self.assertEqual(response.status_code, status.HTTP_401_UNAUTHORIZED)
