from celery import shared_task
import json
import os
from .models import Restaurant

@shared_task
def YelpLoad():
    file_path = os.path.join(os.path.dirname(__file__), "yelp_dataset/yelp_academic_dataset_business.json")

    with open(file_path, "r") as file:
        for idx, line in enumerate(file):
            line = line.strip()
            if line:
                try:
                    data = json.loads(line)
                    if "Restaurants" in data["categories"] and not (Restaurant.objects.filter(yelp_id=data["business_id"]).exists()):
                        Restaurant.objects.create(yelp_id=data["business_id"],
                                                    name=data["name"],
                                                    address=data["address"],
                                                    state=data["state"],
                                                    city=data["city"],
                                                    latitude=data["latitude"],
                                                    longitude=data["longitude"],
                                                    categories=data["categories"],
                                                    stars=data["stars"],
                                                    review_count=data["review_count"]
                                                    )
                except:
                    print("line didnt work")
        print("End Load")