from rest_framework.test import APIRequestFactory, force_authenticate
from django.contrib.auth.models import User  # Import User model or your custom user model
from portfolio.views import AssetListCreateView

# Using the standard RequestFactory API to create a form POST request
user = User.objects.create_user(username='testuser', password='testpassword')

factory = APIRequestFactory()
request = factory.post('/api/assets/', {'ticker': 'VTI', 
                                          'shares': 10, 
                                          'costbasis': 200, 
                                          'buy': '2023-10-13',
                                          'user': user.id}, format='json')

force_authenticate(request, user=user)

view = AssetListCreateView.as_view()  # Replace with your actual view

# Send the request to the view
response = view(request)
print("Response: " + str(response.data))

# Now, you can assert the response to check if it meets your expectations
# For example, you might check response status code, content, etc.
assert response.status_code == 201 