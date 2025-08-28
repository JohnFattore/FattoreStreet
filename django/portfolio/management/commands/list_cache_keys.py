from django.core.management.base import BaseCommand
from django.core.cache import cache

class Command(BaseCommand):
    help = 'List all Redis cache keys'

    def handle(self, *args, **options):
        client = cache.client.get_client()
        for key in client.scan_iter("*"):
            print(key)