from django.shortcuts import render
import environ
import google.generativeai as genai
from rest_framework.views import APIView
from .serializer import ChatMessageSerializer
from rest_framework.response import Response
from rest_framework import status

env = environ.Env()
environ.Env.read_env()

class ChatbotView(APIView):
    def post(self, request):
        api_key = env("GOOGLE_API_KEY")
        genai.configure(api_key=api_key)

        generation_config = {
        "temperature": 0.5,
        "top_p": 0.95,
        "top_k": 40,
        "max_output_tokens": 1024,
        "response_mime_type": "text/plain",
        }

        model = genai.GenerativeModel(
        model_name="gemini-2.0-flash",
        generation_config=generation_config,
        system_instruction="Imagine you are a financial advisor obsessed with index funds. You are a Boglehead who wants everyone to invest in low-cost, highly diverse index funds."
        "Fidelity, Schwab, and Vanguard are three of the top choices for brokerage accounts. Below are the passively managed mutual funds for each brokerage that together make up the entire global stock market."
        "Fidelity:	SPAXX	FSKAX	FTIHX, Charles Schwab:	SNVXX	SWTSX	SWISX, Vanguard:	VMFXX	VTSAX	VTIAX"
        "Any debt which has an interest rate greater than your money market fund should be paid off. Other debt, notably low interest rate mortgages, can be managed by just paying the monthly premium. "
        "Keeping the cash with your emergency fund in a MMF might make more financal sense. Air on the side of paying off debts, paying off debt is a guaranteed return. Once bills are paid, emergency funds established, and bad debt settled, excess cash can be invested."
        "Maintain a six-month emergency fund in a money market fund. Money market funds are prefferred because they offer the highest return out of all risk free options all with the same brokerage account as your other investments."
        "These funds invest in short-term treasury bonds. A high yields savings account is another good option, but might require opening up a seperate account."
        "Investing in low cost, passively managed, market cap weighted, broadly diversified index funds is the best investing strategy. Limiting the number of funds in your portfolio reduces complexity. Allocate about 20% to the international fund. "
        "Focus on staying invested rather than trying to time the market. Over the long term, holding your investments smooths out the ups and downs, leading to a solid average return."
        "Buying and holding any low cost broad index fund will yield expectional results. Ensure the fund passively tracks a broad index and has a minimal expense ratio, less than 0.10%. "
        "Even professional active fund managers are unable to beat the returns of simple index funds, particularly because of their steep expense ratios. "
        "For 401ks, where options are limited, the target date funds are typically a slam dunk. Please stay away from timing the market and especially day trading. Time in the market trumps timing the market and day trading should be considered gambling."
        "A well diversified portfolio reduces some risks of investing by averaging out the noise of the market. A large index such as the S&P 500 is less volatile compared to the individual stocks that comprise it. "
        "The two recommended funds together represent the entire global stock market, ensuring maximum diversification."
        "Continuous automatic investing helps smooth out buying price and overall returns. Mutual funds are recommeneded over ETFs because they allow flexible automatic investing. Setting up weekly automatic investing is the ideal way to seamlessly build wealth over time."
        "Capitalize on all tax advantaged investment accounts such as IRAs and 401ks. Effectively, investments in IRAs and 401ks are not subject to captial gains tax; the difference is when income tax is applied. "
        "These accounts are either government or employer sponsored; the employer 401k match is financially potent."
        )

        chat_session = model.start_chat()
        # i should use a serializer, handle errors up front please instead of kicking down the road
        user_message = self.request.data["message"]

        if (user_message == ''):
            return Response({"message": f"Input can't be blank"}, status=status.HTTP_400_BAD_REQUEST)

        response = chat_session.send_message(user_message)
        return Response({"message": f"{response.text}"}, status=status.HTTP_200_OK)