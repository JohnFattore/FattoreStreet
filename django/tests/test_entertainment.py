from django.conf import settings
from django.db import IntegrityError, transaction
from django.urls import reverse

from entertainment.models import Recommendation
from tests.base import BaseAPITestCase


class RecommendationListViewTests(BaseAPITestCase):
    """Tests for the public recommendation list template view."""

    def setUp(self):
        super().setUp()
        self.url = reverse("entertainment:recommendation_list")

    def test_returns_200_and_renders_template(self):
        response = self.client.get(self.url)
        self.assertEqual(response.status_code, 200)
        self.assertTemplateUsed(response, "entertainment/recommendation_list.html")

    def test_groups_by_type_label_and_drops_empty_groups(self):
        Recommendation.objects.create(type="BOOK", title="Dune", artist="Frank Herbert")
        Recommendation.objects.create(type="MOVIE", title="Heat", artist="Michael Mann")
        Recommendation.objects.create(type="MOVIE", title="Ronin", artist="John Frankenheimer")

        response = self.client.get(self.url)
        groups = response.context["groups"]

        self.assertEqual(list(groups.keys()), ["Book", "Movie"])
        self.assertEqual(len(groups["Movie"]), 2)

    def test_group_order_follows_type_choices(self):
        # Created movie-first, but Book precedes Movie in Type.choices
        Recommendation.objects.create(type="MOVIE", title="Heat", artist="Michael Mann")
        Recommendation.objects.create(type="BOOK", title="Dune", artist="Frank Herbert")
        Recommendation.objects.create(type="SHOW", title="The Wire", artist="David Simon")

        response = self.client.get(self.url)

        self.assertEqual(list(response.context["groups"].keys()), ["Book", "Movie", "TV Show"])

    def test_recommendations_sorted_by_title_within_group(self):
        # View orders by title, overriding the model's -created_at default
        Recommendation.objects.create(type="BOOK", title="Zorba the Greek", artist="Nikos Kazantzakis")
        Recommendation.objects.create(type="BOOK", title="Anathem", artist="Neal Stephenson")

        response = self.client.get(self.url)
        titles = [rec.title for rec in response.context["groups"]["Book"]]

        self.assertEqual(titles, ["Anathem", "Zorba the Greek"])

    def test_home_url_in_context(self):
        response = self.client.get(self.url)
        self.assertEqual(response.context["home_url"], settings.HOME_URL)

    def test_empty_db_renders_no_groups(self):
        response = self.client.get(self.url)
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.context["groups"], {})


class RecommendationModelTests(BaseAPITestCase):
    def test_unique_constraint_on_type_title_artist(self):
        Recommendation.objects.create(type="BOOK", title="Dune", artist="Frank Herbert")
        with self.assertRaises(IntegrityError):
            with transaction.atomic():
                Recommendation.objects.create(type="BOOK", title="Dune", artist="Frank Herbert")

    def test_same_title_allowed_for_different_type(self):
        Recommendation.objects.create(type="BOOK", title="Dune", artist="Frank Herbert")
        Recommendation.objects.create(type="MOVIE", title="Dune", artist="Denis Villeneuve")
        self.assertEqual(Recommendation.objects.count(), 2)

    def test_str_representation(self):
        rec = Recommendation.objects.create(type="BOOK", title="Dune", artist="Frank Herbert")
        self.assertEqual(str(rec), "Dune — Frank Herbert (Book)")

    def test_default_ordering_newest_first(self):
        Recommendation.objects.create(type="BOOK", title="First", artist="A")
        Recommendation.objects.create(type="BOOK", title="Second", artist="B")
        self.assertEqual(Recommendation.objects.first().title, "Second")
