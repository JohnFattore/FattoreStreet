from collections import OrderedDict

from django.conf import settings
from django.shortcuts import render

from .models import Recommendation


def recommendation_list(request):
    groups = OrderedDict(
        (label, []) for _, label in Recommendation.Type.choices
    )
    value_to_label = dict(Recommendation.Type.choices)
    for rec in Recommendation.objects.all().order_by("title"):
        groups[value_to_label[rec.type]].append(rec)

    groups = {label: recs for label, recs in groups.items() if recs}

    return render(
        request,
        "entertainment/recommendation_list.html",
        {"groups": groups, "home_url": settings.HOME_URL},
    )