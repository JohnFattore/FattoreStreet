from django.urls import path

from . import views

urlpatterns = [
    path("api/posts/", views.PostListView.as_view(), name="blog_posts"),
    path("api/posts/<slug:slug>/", views.PostDetailView.as_view(), name="blog_post"),
    path("api/categories/", views.CategoryListView.as_view(), name="blog_categories"),
    path("api/tags/", views.TagListView.as_view(), name="blog_tags"),
]

