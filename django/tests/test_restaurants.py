from django.urls import reverse
from rest_framework import status
from django.contrib.auth.models import User
from restaurants.models import Restaurant, Review
from tests.base import BaseAPITestCase


class RestaurantListTest(BaseAPITestCase):
    """Tests for listing restaurants filtered by state/city."""

    def setUp(self):
        super().setUp()
        self.url = reverse('restaurants')
        Restaurant.objects.create(
            yelp_id='abc123', name='Nashville Bites', address='123 Main St',
            state='TN', city='Nashville', categories='Food', stars=4.0, review_count=10,
        )
        Restaurant.objects.create(
            yelp_id='def456', name='NYC Eats', address='456 Oak Ave',
            state='NY', city='New York', categories='Food', stars=3.5, review_count=5,
        )

    def test_filter_by_state_and_city(self):
        response = self.client.get(self.url, {'state': 'TN', 'city': 'Nashville'})
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(len(response.data), 1)
        self.assertEqual(response.data[0]['name'], 'Nashville Bites')

    def test_filter_no_results(self):
        response = self.client.get(self.url, {'state': 'CA', 'city': 'Los Angeles'})
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(len(response.data), 0)


class ReviewCreateTest(BaseAPITestCase):
    """Tests for creating reviews."""

    def setUp(self):
        super().setUp()
        self.url = reverse('reviewCreate')
        self.restaurant = Restaurant.objects.create(
            yelp_id='abc123', name='Nashville Bites', address='123 Main St',
            state='TN', city='Nashville', categories='Food', stars=4.0, review_count=10,
        )

    def test_create_review(self):
        self.authenticate_client()
        data = {
            'restaurant': self.restaurant.id,
            'user': self.user.id,
            'rating': '4.0',
            'comment': 'Great food!',
        }
        response = self.client.post(self.url, data, format='json')
        self.assertEqual(response.status_code, status.HTTP_201_CREATED)
        self.assertEqual(Review.objects.count(), 1)
        review = Review.objects.get()
        self.assertEqual(review.user, self.user)
        self.assertEqual(review.restaurant, self.restaurant)

    def test_create_review_unauthenticated(self):
        self.unauthenticate_client()
        data = {
            'restaurant': self.restaurant.id,
            'user': self.user.id,
            'rating': '4.0',
            'comment': 'Great food!',
        }
        response = self.client.post(self.url, data, format='json')
        self.assertEqual(response.status_code, status.HTTP_401_UNAUTHORIZED)

    def test_duplicate_review_rejected(self):
        """A user cannot review the same restaurant twice."""
        self.authenticate_client()
        data = {
            'restaurant': self.restaurant.id,
            'user': self.user.id,
            'rating': '4.0',
            'comment': 'Great food!',
        }
        self.client.post(self.url, data, format='json')
        response = self.client.post(self.url, data, format='json')
        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)
        self.assertIn("already reviewed", str(response.data).lower())


class ReviewListTest(BaseAPITestCase):
    """Tests for listing a user's own reviews."""

    def setUp(self):
        super().setUp()
        self.url = reverse('reviewList')
        self.restaurant = Restaurant.objects.create(
            yelp_id='abc123', name='Nashville Bites', address='123 Main St',
            state='TN', city='Nashville', categories='Food', stars=4.0, review_count=10,
        )
        Review.objects.create(restaurant=self.restaurant, user=self.user, rating='3.0', comment='OK')

    def test_list_own_reviews(self):
        self.authenticate_client()
        response = self.client.get(self.url)
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(len(response.data), 1)
        self.assertEqual(response.data[0]['comment'], 'OK')

    def test_does_not_list_other_users_reviews(self):
        """User should only see their own reviews, not another user's."""
        other_user = User.objects.create_user(username='otheruser', password='pass')
        other_restaurant = Restaurant.objects.create(
            yelp_id='xyz789', name='Other Place', address='789 Elm St',
            state='TN', city='Nashville', categories='Food', stars=3.0, review_count=2,
        )
        Review.objects.create(restaurant=other_restaurant, user=other_user, rating='5.0')
        self.authenticate_client()
        response = self.client.get(self.url)
        self.assertEqual(len(response.data), 1)


class ReviewDeleteTest(BaseAPITestCase):
    """Tests for deleting reviews (ownership enforced)."""

    def setUp(self):
        super().setUp()
        self.restaurant = Restaurant.objects.create(
            yelp_id='abc123', name='Nashville Bites', address='123 Main St',
            state='TN', city='Nashville', categories='Food', stars=4.0, review_count=10,
        )
        self.review = Review.objects.create(
            restaurant=self.restaurant, user=self.user, rating='3.0', comment='OK',
        )
        self.url = reverse('review', kwargs={'pk': self.review.id})

    def test_delete_own_review(self):
        self.authenticate_client()
        response = self.client.delete(self.url)
        self.assertEqual(response.status_code, status.HTTP_204_NO_CONTENT)
        self.assertEqual(Review.objects.count(), 0)

    def test_cannot_delete_other_users_review(self):
        other_user = User.objects.create_user(username='otheruser', password='pass')
        other_review = Review.objects.create(
            restaurant=self.restaurant, user=other_user, rating='2.0',
        )
        self.authenticate_client()
        url = reverse('review', kwargs={'pk': other_review.id})
        response = self.client.delete(url)
        self.assertIn(response.status_code, [status.HTTP_403_FORBIDDEN, status.HTTP_404_NOT_FOUND])
        self.assertEqual(Review.objects.filter(user=other_user).count(), 1)


class ReviewUpdateTest(BaseAPITestCase):
    """Tests for updating reviews (ownership enforced)."""

    def setUp(self):
        super().setUp()
        self.restaurant = Restaurant.objects.create(
            yelp_id='abc123', name='Nashville Bites', address='123 Main St',
            state='TN', city='Nashville', categories='Food', stars=4.0, review_count=10,
        )
        self.review = Review.objects.create(
            restaurant=self.restaurant, user=self.user, rating='3.0', comment='OK',
        )
        self.url = reverse('reviewUpdate', kwargs={'pk': self.review.id})

    def test_cannot_update_other_users_review(self):
        """IsOwner permission should block updates to reviews owned by others."""
        other_user = User.objects.create_user(username='otheruser', password='pass')
        other_review = Review.objects.create(
            restaurant=self.restaurant, user=other_user, rating='2.0',
        )
        self.authenticate_client()
        url = reverse('reviewUpdate', kwargs={'pk': other_review.id})
        data = {'rating': '1.0', 'comment': 'Hacked', 'restaurant': self.restaurant.id}
        response = self.client.put(url, data, format='json')
        self.assertIn(response.status_code, [status.HTTP_403_FORBIDDEN, status.HTTP_404_NOT_FOUND])
