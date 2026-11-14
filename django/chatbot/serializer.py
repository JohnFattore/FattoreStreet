from rest_framework import serializers
from .models import Conversation, Interaction
from .prompt import COMPARISON_FIGURES

class ChatMessageSerializer(serializers.Serializer):
    message = serializers.CharField(max_length=500)  # Adjust max_length as needed

class InteractionSerializer(serializers.ModelSerializer):
    class Meta:
        model = Interaction
        fields = ['input_text', 'output_text', 'timestamp']

class ConversationSerializer(serializers.ModelSerializer):
    class Meta:
        model = Conversation
        fields = ['id', 'title', 'created_at', 'last_message_at']
        read_only_fields = ['id', 'created_at', 'last_message_at']

class ConversationDetailSerializer(ConversationSerializer):
    interactions = InteractionSerializer(many=True, read_only=True)

    class Meta(ConversationSerializer.Meta):
        fields = ConversationSerializer.Meta.fields + ['interactions']


# Fact-sheet figures by kind: money, EPS and multiples are plain numbers, percentages carry a "%" ("24.46%"),
# and dates are ISO. The fact sheet writes "N/A" for a ratio it couldn't compute, which the prompt
# shows as not available. Checking each kind separately keeps "24%" out of a money figure, which
# the prompt could not format.
FIGURE_PATTERNS = {
    "money": r"^-?\d{1,20}(\.\d{1,10})?$",
    "eps": r"^-?\d{1,20}(\.\d{1,10})?$",
    "ratio": r"^-?\d{1,20}(\.\d{1,10})?$",
    "percent": r"^(-?\d{1,20}(\.\d{1,10})?%|N/A)$",
    "date": r"^\d{4}-\d{2}-\d{2}$",
}


class CompanyFiguresSerializer(serializers.Serializer):
    """One company's comparison figures. Unknown keys are dropped; blank or null means not reported."""
    ticker = serializers.RegexField(r"^[A-Z0-9][A-Z0-9.\-]{0,9}$")

    def get_fields(self):
        fields = super().get_fields()
        for key, (_, kind) in COMPARISON_FIGURES.items():
            fields[key] = serializers.RegexField(
                FIGURE_PATTERNS[kind],
                max_length=32,
                required=False,
                allow_blank=True,
                allow_null=True,
            )
        return fields


class CompanyComparisonSerializer(serializers.Serializer):
    companies = serializers.ListField(child=CompanyFiguresSerializer(), min_length=2, max_length=2)
