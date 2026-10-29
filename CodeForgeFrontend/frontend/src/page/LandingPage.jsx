import { useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { clearAccessToken, useAuthStatus } from "../api";
import {
  FaReact,
  FaAngular,
  FaNodeJs,
  FaVuejs,
} from "react-icons/fa";
import { SiNextdotjs, SiSpringboot, SiVite } from "react-icons/si";

const frameworks = [
  {
    name: "React",
    mark: <FaReact />,
    tone: "react",
    type: "Frontend framework",
    description: "Build modern interactive web applications.",
    command: "Create React project",
  },
  {
    name: "Angular",
    mark: <FaAngular />,
    tone: "angular",
    type: "Frontend framework",
    description: "The platform for ambitious web experiences.",
    command: "Create Angular project",
  },
  {
    name: "Vue",
    mark: <FaVuejs />,
    tone: "vue",
    type: "Frontend framework",
    description: "A progressive framework for every builder.",
    command: "Create Vue project",
  },
  {
    name: "Next.js",
    mark: <SiNextdotjs />,
    tone: "next",
    type: "Full-stack framework",
    description: "Ship production-ready React at speed.",
    command: "Create Next.js project",
  },
  {
    name: "Node.js",
    mark: <FaNodeJs />,
    tone: "node",
    type: "Backend runtime",
    description: "Build fast, scalable network services.",
    command: "Create Node.js project",
  },
  {
    name: "Spring Boot",
    mark: <SiSpringboot />,
    tone: "spring",
    type: "Backend framework",
    description: "Production-grade Java, without the ceremony.",
    command: "Create Spring project",
  },
];

const projectTypes = [
  {
    mark: <FaReact />,
    name: "React App",
    description: "Interactive UI",
    framework: "React",
  },
  {
    mark: <FaAngular />,
    name: "Angular App",
    description: "Structured web",
    framework: "Angular",
  },
  {
    mark: <FaVuejs />,
    name: "Vue App",
    description: "Progressive UI",
    framework: "Vue",
  },
  {
    mark: <SiNextdotjs />,
    name: "Next.js App",
    description: "Full-stack web",
    framework: "Next.js",
  },
  {
    mark: <FaNodeJs />,
    name: "Node.js API",
    description: "Server runtime",
    framework: "Node.js",
  },
  {
    mark: <SiSpringboot />,
    name: "Spring Boot",
    description: "Java service",
    framework: "Spring Boot",
  },
  {
    mark: <SiVite />,
    name: "Vite Project",
    description: "Lightning fast",
    framework: null,
  },
];

function LandingPage() {
  const [activeIndex, setActiveIndex] = useState(0);
  const isAuthenticated = useAuthStatus();

  const dragStart = useRef(null);

  const active = frameworks[activeIndex];

  // --------------------------------------------------
  // Carousel navigation
  // --------------------------------------------------

  const move = (direction) => {
    setActiveIndex(
      (current) =>
        (current + direction + frameworks.length) %
        frameworks.length
    );
  };

  // --------------------------------------------------
  // Keyboard navigation
  // --------------------------------------------------

  useEffect(() => {
    const handleKeyDown = (event) => {
      if (event.key === "ArrowLeft") {
        move(-1);
      }

      if (event.key === "ArrowRight") {
        move(1);
      }
    };

    window.addEventListener("keydown", handleKeyDown);

    return () => {
      window.removeEventListener("keydown", handleKeyDown);
    };
  }, []);

  // --------------------------------------------------
  // Get framework based on carousel position
  // --------------------------------------------------

  const getCard = (offset) => {
    return frameworks[
      (activeIndex + offset + frameworks.length) %
        frameworks.length
    ];
  };

  // --------------------------------------------------
  // Select framework from project tile
  // --------------------------------------------------

  const selectFramework = (frameworkName) => {
    const index = frameworks.findIndex(
      (framework) => framework.name === frameworkName
    );

    if (index !== -1) {
      setActiveIndex(index);
    }
  };

  return (
    <main className="landing-shell">

      {/* ==================================================
          BACKGROUND
      ================================================== */}

      <div className="ambient ambient-one" />
      <div className="ambient ambient-two" />
      <div className="blueprint-grid" />

      {/* ==================================================
          NAVIGATION
      ================================================== */}

      <nav className="topbar">

        {/* Brand */}

        <a
          className="brand"
          href="#top"
          aria-label="CodeForge home"
        >
          <span className="brand-mark">
            ⌘
          </span>

          CodeForge
        </a>

        {/* Navigation Links */}

        <div className="nav-links">

          <a href="#projects">
            Projects
          </a>

          <a href="#templates">
            Templates
          </a>

          <a href="#docs">
            Documentation
          </a>

          <a
            href="https://github.com"
            target="_blank"
            rel="noreferrer"
          >
            GitHub ↗
          </a>

        </div>

        {/* Navigation Actions */}

        <div className="nav-actions">

          {isAuthenticated ? (
            <>
              <Link className="start-button" to="/create">
                Create project <span>↗</span>
              </Link>
              <button className="sign-in" type="button" onClick={clearAccessToken}>
                Log out
              </button>
            </>
          ) : (
            <>
              <Link className="sign-in" to="/login">Sign in</Link>
              <Link className="start-button" to="/register">
                Get started <span>↗</span>
              </Link>
            </>
          )}

        </div>

      </nav>

      {/* ==================================================
          HERO
      ================================================== */}

      <section
        className="hero"
        id="top"
      >

        <div className="eyebrow">

          <span className="status-dot" />

          YOUR CLOUD WORKSPACE

          <span className="eyebrow-rule" />

          {String(activeIndex + 1).padStart(2, "0")} / 06

        </div>

        <h1>
          Choose what you
          <br />

          <em>
            want to build.
          </em>
        </h1>

        <p className="hero-copy">
          Your cloud development environment,
          built for every stack.

          <br />

          Pick a framework and start shaping
          what comes next.
        </p>

      </section>

      {/* ==================================================
          FRAMEWORK CAROUSEL
      ================================================== */}

      <section
        className="carousel-section"
        id="projects"
        aria-label="Choose a framework"
      >

        {/* Previous Button */}

        <button
          className="carousel-arrow arrow-left"
          onClick={() => move(-1)}
          aria-label="Previous framework"
        >
          ←
        </button>

        {/* Carousel Track */}

        <div
          className="carousel-track"

          onPointerDown={(event) => {
            dragStart.current = event.clientX;
          }}

          onPointerUp={(event) => {

            if (
              dragStart.current !== null &&
              Math.abs(
                event.clientX - dragStart.current
              ) > 40
            ) {

              move(
                event.clientX < dragStart.current
                  ? 1
                  : -1
              );

            }

            dragStart.current = null;
          }}
        >

          {[-1, 0, 1].map((offset) => {

            const card = getCard(offset);

            const positionClass =
              offset === 0
                ? "active"
                : offset < 0
                ? "left"
                : "right";

            const cardNumber =
              ((activeIndex +
                offset +
                frameworks.length) %
                frameworks.length) + 1;

            return (

              <article
                className={`
                  framework-card
                  ${positionClass}
                  ${card.tone}
                `}
                key={`${card.name}-${offset}`}
              >

                {/* Card Top */}

                <div className="card-top">

                  <span className="framework-mark">
                    {card.mark}
                  </span>

                  <span className="card-index">
                    {String(cardNumber).padStart(
                      2,
                      "0"
                    )}

                    {" / 06"}
                  </span>

                </div>

                {/* Card Content */}

                <div className="card-content flex flex-col items-start">

                  <span className="framework-type">
                    {card.type}
                  </span>

                  <h2>
                    {card.name}
                  </h2>

                  <p>
                    {card.description}
                  </p>

                  {/* Active Card Button */}

                  {offset === 0 && (

                    <Link
                      className="create-button"
                      to="/create"
                      state={{ projectType: card.name === "Next.js" ? "next" : card.name === "Node.js" ? "node" : card.name === "Spring Boot" ? "spring" : card.name.toLowerCase() }}
                    >
                      {card.command}

                      <span>
                        →
                      </span>
                    </Link>

                  )}

                </div>

                {/* Card Glow */}

                <div className="card-glow" />

              </article>
            );
          })}

        </div>

        {/* Next Button */}

        <button
          className="carousel-arrow arrow-right"
          onClick={() => move(1)}
          aria-label="Next framework"
        >
          →
        </button>

      </section>

      {/* ==================================================
          CAROUSEL META
      ================================================== */}

      <div className="carousel-meta">

        <span>
          DRAG TO EXPLORE
        </span>

        <div className="progress">

          <span
            style={{
              width: `${
                ((activeIndex + 1) /
                  frameworks.length) *
                100
              }%`,
            }}
          />

        </div>

        <span>

          {String(activeIndex + 1).padStart(
            2,
            "0"
          )}

          <b>
            {" / 06"}
          </b>

        </span>

      </div>

      {/* ==================================================
          PROJECT STACK SECTION
      ================================================== */}

      <section
        className="stack-section"
        id="templates"
      >

        {/* Section Heading */}

        <div className="section-heading">

          <div>

            <span className="mini-label">
              STARTING POINTS
            </span>

            <h3>
              Build from a stack you know.
            </h3>

          </div>

          <a href="#templates">

            View all templates

            <span>
              ↗
            </span>

          </a>

        </div>

        {/* Project Grid */}

        <div className="project-grid">

          {projectTypes.map(
            ({
              mark,
              name,
              description,
              framework,
            }) => (

              <button
                className={`
                  project-tile
                  ${
                    framework === active.name
                      ? "selected"
                      : ""
                  }
                `}
                key={name}
                onClick={() => framework && selectFramework(framework)}
              >

                {/* Icon */}

                <span className="tile-mark">
                  {mark}
                </span>

                {/* Text */}

                <span>

                  <strong>
                    {name}
                  </strong>

                  <small>
                    {description}
                  </small>

                </span>

                {/* Arrow */}

                <span className="tile-arrow">
                  ↗
                </span>

              </button>

            )
          )}

        </div>

      </section>

      {/* ==================================================
          DOCUMENTATION
      ================================================== */}

      <section
        id="docs"
        className="docs-section"
      >
        {/* Add your documentation content here */}
      </section>

      {/* ==================================================
          FOOTER
      ================================================== */}

      <footer>

        <span>
          CODEFORGE / CLOUD IDE
        </span>

        <span>
          BUILD. CODE. DEPLOY.
        </span>

        <span>
          © 2026 CODEFORGE
        </span>

      </footer>

    </main>
  );
}

export default LandingPage;