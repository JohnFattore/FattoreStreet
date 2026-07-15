import re
from rest_framework import serializers
from django.core.validators import RegexValidator
from .models import Asset, Account
from django.utils import timezone
import logging
from .helper import get_historical_prices, is_market_open

logger = logging.getLogger(__name__)

TICKER_REGEX = RegexValidator(
    regex=r'^[A-Z][A-Z0-9.\-]{0,9}$',
    message="Ticker must be 1-10 uppercase letters, digits, dots, or hyphens.",
)


class TickerQuerySerializer(serializers.Serializer):
    """Reusable serializer for validating a single ticker query parameter."""
    ticker = serializers.CharField(validators=[TICKER_REGEX])


class SymbolQuerySerializer(serializers.Serializer):
    """Reusable serializer for validating a single symbol query parameter."""
    symbol = serializers.CharField(validators=[TICKER_REGEX])


class AccountSerializer(serializers.ModelSerializer):
    user = serializers.PrimaryKeyRelatedField(read_only=True)

    class Meta:
        model = Account
        fields = ['id', 'name', 'account_type', 'user']

# serializer for Asset Model
class AssetSerializer(serializers.ModelSerializer):
    user = serializers.PrimaryKeyRelatedField(read_only=True)
    buy_price = serializers.SerializerMethodField()
    sell_price = serializers.SerializerMethodField()
    buy_SnP500 = serializers.SerializerMethodField()
    sell_SnP500 = serializers.SerializerMethodField()

    class Meta:
        model = Asset
        fields = ['id', 
                  'ticker',
                  'shares', 
                  'buy_date',
                  'buy_price',
                  'buy_SnP500',
                  'sell_date',
                  'sell_price',
                  'sell_SnP500',
                  'user',
                  'account']

    def get_buy_price(self, obj):
        try:
            prices = get_historical_prices([obj.ticker])[obj.ticker]
            return prices[obj.buy_date.strftime("%Y-%m-%d")] * obj.shares
        except (KeyError, TypeError) as e:
            logger.warning("Could not resolve buy price for %s on %s: %s", obj.ticker, obj.buy_date, e)
            return None

    def get_buy_SnP500(self, obj):
        try:
            prices = get_historical_prices(["SPY"])["SPY"]
            return prices[obj.buy_date.strftime("%Y-%m-%d")] 
        except (KeyError, TypeError) as e:
            logger.warning("Could not resolve SPY buy price on %s: %s", obj.buy_date, e)
            return None

    def get_sell_price(self, obj):
        if obj.sell_date:
            try:
                prices = get_historical_prices([obj.ticker])[obj.ticker]
                return prices[obj.sell_date.strftime("%Y-%m-%d")] * obj.shares
            except (KeyError, TypeError) as e:
                logger.warning("Could not resolve sell price for %s on %s: %s", obj.ticker, obj.sell_date, e)
                return None
        return None

    def get_sell_SnP500(self, obj):
        if obj.sell_date:
            try:
                prices = get_historical_prices(["SPY"])["SPY"]
                return prices[obj.sell_date.strftime("%Y-%m-%d")] 
            except (KeyError, TypeError) as e:
                logger.warning("Could not resolve SPY sell price on %s: %s", obj.sell_date, e)
                return None
        return None

    def validate_ticker(self, value):
        if not re.match(r'^[A-Z][A-Z0-9.\-]{0,9}$', value):
            raise serializers.ValidationError("Ticker must be 1-10 uppercase letters, digits, dots, or hyphens.")
        return value

    def validate_shares(self, value):
        if value <= 0:
            raise serializers.ValidationError("The number of shares must be greater than 0.")
        return value
    
    def validate_buy_date(self, value):
        if value > timezone.now().date():
            raise serializers.ValidationError("The buy date can't be in the future.")
        if not is_market_open(value):
            raise serializers.ValidationError(f"Market was closed on {value}.")
        return value
    
    def validate_sell_date(self, value):
        if value > timezone.now().date():
            raise serializers.ValidationError("The sell date can't be in the future.")
        if not is_market_open(value):
            raise serializers.ValidationError(f"Market closed on {value}")
        return value
        
    def validate(self, data):
        buy_date = data.get('buy_date', getattr(self.instance, 'buy_date', None))
        sell_date = data.get('sell_date', getattr(self.instance, 'sell_date', None))

        if buy_date and sell_date and sell_date < buy_date:
            raise serializers.ValidationError("The sell date can't be before the buy date.")
        
        return data