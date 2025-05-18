import Container from 'react-bootstrap/Container';
import Nav from 'react-bootstrap/Nav';
import Navbar from 'react-bootstrap/Navbar';
import LogoutButton from './LogoutButton';
import { useSelector } from "react-redux";
import { RootState } from '../main';
export default function TopNavigation() {

  const { access } = useSelector((state: RootState) => state.user);

  return (
    <Navbar variant="light" expand="lg">
      <Container>
        <Navbar.Toggle aria-controls="basic-navbar-nav" />
        <Navbar.Collapse id="basic-navbar-nav">
          <Nav className="me-auto">
            <Nav.Link href="/">Home</Nav.Link>
            <Nav.Link href="portfolio">Portfolio</Nav.Link>
            <Nav.Link href="watchlist">Watch List</Nav.Link>
            <Nav.Link href="chatbot">Chatbot</Nav.Link>
            {!access && <Nav.Link href="register">Register</Nav.Link>}
            <Nav.Link href="restaurants">Restaurants</Nav.Link>
            <Nav.Link href="entertainment">Entertainment</Nav.Link>
            {access && <LogoutButton />}
          </Nav>
        </Navbar.Collapse>
      </Container>
    </Navbar>
  );
}