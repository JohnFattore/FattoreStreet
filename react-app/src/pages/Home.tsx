import { Card } from 'react-bootstrap';

export default function Home() {
    return (
        <>
            <h1 style={{ textAlign: 'center' }}>Welcome to Fattore Street!</h1>
            <Card>
                <Card.Title>
                    "By periodically investing in an index fund, for example, the know-nothing investor can actually out-perform most investment professionals.
                    Paradoxically, when ‘dumb’ money acknowledges its limitations, it ceases to be dumb." - Warren Buffett
                </Card.Title>
            </Card>
            <h5>The goal of Fattore Street is to provide education about the power of low cost index funds. The following list explains the different pages, which can be accessed with the top navigation bar.
            </h5>
            <ul>
                <li>New to the site? Create an account on the Register page</li>
                <li>Portfolio is a stock/bond paper trader, where you can create a hypothetical portfolio and compare it to the broader market</li>
                <li>Chatbot allows you to chat with a Boglehead bot that will answer all your index fund questions</li>
                <li>Watch List is a live stock/bond tracker</li>
                <li>Suggestions provides basic advice about investing in low cost index funds</li>
                <li>Restaurants/Reviews is a Nashville restaurant recommendator. Add your restaurant reviews on Restaurants, then go to Reviews to generate recommendations</li>
            </ul>
        </>
    );
}