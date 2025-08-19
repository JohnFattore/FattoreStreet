import Container from 'react-bootstrap/Container';
import Nav from 'react-bootstrap/Nav';
import Navbar from 'react-bootstrap/Navbar';
import LogoutButton from './LogoutButton';
import { useSelector } from "react-redux";
import { RootState } from '../main';
import { Link } from 'react-router-dom';

export default function TopNavigation() {

  const { access } = useSelector((state: RootState) => state.user);

  return (
    <Navbar expand="lg">
      <Container>
        <Navbar.Toggle aria-controls="basic-navbar-nav" />
        <Navbar.Collapse id="basic-navbar-nav">
          <Nav className="me-auto">
            <Nav.Link as={Link} to="/">Home</Nav.Link>
            <Nav.Link as={Link} to="/portfolio">Portfolio</Nav.Link>
            <Nav.Link as={Link} to="/visualizer">Visualizer</Nav.Link>
            <Nav.Link as={Link} to="/watchlist">Watchlist</Nav.Link>
            <Nav.Link as={Link} to="/economic-indicators">Economic Indicators</Nav.Link>
            <Nav.Link as={Link} to="/chatbot">Chatbot</Nav.Link>
            {!access && <Nav.Link as={Link} to="/register">Register</Nav.Link>}
            <Nav.Link as={Link} to="/restaurants">Restaurants</Nav.Link>
            <Nav.Link as={Link} to="/entertainment">Entertainment</Nav.Link>
            {access && <LogoutButton />}
          </Nav>
        </Navbar.Collapse>
      </Container>
    </Navbar>
  );
}
