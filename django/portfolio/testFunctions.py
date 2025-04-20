import datetime
from unittest.mock import patch
import pandas as pd

from django.test import TestCase
from portfolio.models import SnP500Price
from portfolio.helper import get_or_create_SnP500Price  # Adjust import as needed


class GetOrCreateSnP500PriceTest(TestCase):
    def setUp(self):
        self.test_date = datetime.date(2023, 10, 13)

    @patch('portfolio.helper.yf.Ticker')
    def test_creates_snp500price_if_not_exists(self, mock_ticker):
        # Set up fake data returned by yfinance
        mock_history = pd.DataFrame({
            'Close': {self.test_date.strftime('%Y-%m-%d'): 432.10}
        })
        mock_ticker.return_value.history.return_value = mock_history

        self.assertEqual(SnP500Price.objects.count(), 0)
        result = get_or_create_SnP500Price(self.test_date)
        self.assertIsNotNone(result)
        self.assertEqual(SnP500Price.objects.count(), 1)
        self.assertEqual(result.price, 432.10)

    def test_returns_existing_snp500price(self):
        obj = SnP500Price.objects.create(date=self.test_date, price=400.00)
        result = get_or_create_SnP500Price(self.test_date)
        self.assertEqual(result, obj)
        self.assertEqual(SnP500Price.objects.count(), 1)