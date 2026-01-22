from django.test import TestCase
from django.contrib.auth.models import User
from portfolio.models import Asset, Account
from decimal import Decimal
import datetime

class AccountTestCase(TestCase):
    def setUp(self):
        self.user = User.objects.create(username='testuser')
        self.account = Account.objects.create(name='Test Account', user=self.user, account_type='ROTH_IRA')

    def test_account_creation(self):
        """Test that account is correctly created."""
        self.assertEqual(Account.objects.count(), 1)
        self.assertEqual(self.account.name, 'Test Account')
        self.assertEqual(self.account.user, self.user)
        self.assertEqual(self.account.account_type, 'ROTH_IRA')

    def test_asset_linked_to_account(self):
        """Test that asset can be linked to an account."""
        asset = Asset.objects.create(
            ticker='AAPL',
            shares=Decimal('10.00000'),
            buy_date=datetime.date(2023, 1, 1),
            user=self.user,
            account=self.account
        )
        self.assertEqual(asset.account, self.account)
        self.assertEqual(self.account.asset_set.count(), 1)

    def test_asset_without_account(self):
        """Test that asset can exist without an account (backward compatibility)."""
        asset = Asset.objects.create(
            ticker='GOOG',
            shares=Decimal('5.00000'),
            buy_date=datetime.date(2023, 1, 1),
            user=self.user
        )
        self.assertIsNone(asset.account)
