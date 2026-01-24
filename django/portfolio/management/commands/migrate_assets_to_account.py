from django.core.management.base import BaseCommand
from django.contrib.auth.models import User
from portfolio.models import Account, Asset

class Command(BaseCommand):
    help = 'Migrates assets without an account to a new default account for each user'

    def handle(self, *args, **kwargs):
        users = User.objects.all()
        for user in users:
            # Find assets for this user that don't have an account
            orphaned_assets = Asset.objects.filter(user=user, account__isnull=True)
            
            if orphaned_assets.exists():
                self.stdout.write(f"Found {orphaned_assets.count()} orphaned assets for user: {user.username}")
                
                # Get or create a default account
                account, created = Account.objects.get_or_create(
                    user=user,
                    name="Default Portfolio",
                    defaults={'account_type': 'TAXABLE_ACCOUNT'} # Default to taxable if creating
                )
                
                if created:
                    self.stdout.write(f"  Created new account 'Default Portfolio' for user: {user.username}")
                else:
                    self.stdout.write(f"  Using existing account 'Default Portfolio' for user: {user.username}")
                
                # Update assets to point to this account
                updated_count = orphaned_assets.update(account=account)
                self.stdout.write(self.style.SUCCESS(f"  Migrated {updated_count} assets to account '{account.name}'"))
            else:
                self.stdout.write(f"No orphaned assets found for user: {user.username}")
