from django.core.management.base import BaseCommand
from restaurants.models import Restaurant
import pandas as pd
import os
import glob

class Command(BaseCommand):
    help = 'Populates the Restaurant table using Yelp data from pickle file'

    def handle(self, *args, **kwargs):
        self.stdout.write(self.style.SUCCESS('Starting restaurant population...'))
        
        # Locate the pickle file
        # __file__ is inside django/restaurants/management/commands/
        # so dirname(abspath) -> commands
        # dirname(dirname) -> management
        # dirname(dirname(dirname)) -> restaurants
        base_dir = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
        file_path = os.path.join(base_dir, "yelp_dataset", "reviewsNashville.pkl")
        
        if not os.path.exists(file_path):
            self.stdout.write(self.style.ERROR(f'Pickle file not found at: {file_path}'))
            return

        try:
            self.stdout.write(f'Loading data from {file_path}...')
            df = pd.read_pickle(file_path)
            
            # Aggregate data by restaurant_id
            # metrics needed: name, city, state, categories (take first), rating (mean), count
            valid_df = df.groupby('restaurant_id').agg({
                'name': 'first',
                'city': 'first',
                'state': 'first',
                'categories': 'first',
                'rating': ['mean', 'count']
            }).reset_index()
            
            # Flatten columns
            valid_df.columns = ['restaurant_id', 'name', 'city', 'state', 'categories', 'stars', 'review_count']
            
            count_created = 0
            count_skipped = 0
            
            for index, row in valid_df.iterrows():
                yelp_id = row['restaurant_id']
                
                if not Restaurant.objects.filter(yelp_id=yelp_id).exists():
                    Restaurant.objects.create(
                        yelp_id=yelp_id,
                        name=row['name'],
                        address="Not Available", # Missing in pkl
                        state=row['state'],
                        city=row['city'],
                        latitude=1.0, # Missing in pkl
                        longitude=1.0, # Missing in pkl
                        categories=row['categories'],
                        stars=round(row['stars'] * 2) / 2, # Round to nearest 0.5 for choices
                        review_count=row['review_count']
                    )
                    count_created += 1
                else:
                    count_skipped += 1
                    
                if index % 100 == 0:
                     self.stdout.write(f'Processed {index} restaurants...')

            self.stdout.write(self.style.SUCCESS(f'Successfully created {count_created} restaurants. Skipped {count_skipped} existing.'))

        except Exception as e:
            self.stdout.write(self.style.ERROR(f'Error occurred: {str(e)}'))
