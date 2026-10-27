import { useEffect, useRef } from 'react';
import './GlitterBackground.css';

const GlitterBackground = ({ color = 'rgba(255, 255, 255, 0.8)' }) => {
  const canvasRef = useRef(null);
  const particlesRef = useRef([]);
  const mouseRef = useRef({ x: -1000, y: -1000, active: false });
  const animationFrameRef = useRef(null);

  const CONFIG = {
    particleCount: 800,
    interactionRadius: 150,
    repulsionStrength: 18,
    friction: 0.95,
    returnStrength: 0.005,
    particleSizeMin: 1.5,
    particleSizeMax: 4,
    twinkleSpeed: 0.05,
    floatStrength: 0.15,
    driftX: -0.12,
    driftY: -0.08,
  };

  const createParticles = (canvas) => {
    const particles = [];
    for (let i = 0; i < CONFIG.particleCount; i++) {
      const size = Math.random() * (CONFIG.particleSizeMax - CONFIG.particleSizeMin) + CONFIG.particleSizeMin;
      particles.push({
        x: Math.random() * canvas.width,
        y: Math.random() * canvas.height,
        hoverOffsetX: 0,
        hoverOffsetY: 0,
        originX: 0,
        originY: 0,
        vx: 0,
        vy: 0,
        size,
        baseOpacity: Math.random() * 0.6 + 0.6,
        opacity: Math.random() * 0.6 + 0.6,
        twinklePhase: Math.random() * Math.PI * 2,
        floatAngle: Math.random() * Math.PI * 2,
        floatSpeed: Math.random() * 0.02 + 0.01,
      });
      particles[i].originX = particles[i].x;
      particles[i].originY = particles[i].y;
    }
    return particles;
  };

  const drawSparkle = (ctx, particle) => {
    ctx.save();
    ctx.translate(particle.x, particle.y);
    ctx.fillStyle = color;
    ctx.beginPath();
    ctx.arc(0, 0, particle.size * 0.8, 0, Math.PI * 2);
    ctx.fill();

    ctx.restore();
  };

  const updateParticle = (particle, mouseX, mouseY, width, height) => {
    const dx = particle.x - mouseX;
    const dy = particle.y - mouseY;
    const distance = Math.sqrt(dx * dx + dy * dy);
    let targetOffsetX = 0;
    let targetOffsetY = 0;

    if (distance < CONFIG.interactionRadius && distance > 0 && mouseRef.current.active) {
      const displacement = (1 - distance / CONFIG.interactionRadius) * CONFIG.repulsionStrength;
      targetOffsetX = (dx / distance) * displacement;
      targetOffsetY = (dy / distance) * displacement;
    }

    const previousOffsetX = particle.hoverOffsetX;
    const previousOffsetY = particle.hoverOffsetY;
    particle.hoverOffsetX += (targetOffsetX - particle.hoverOffsetX) * 0.08;
    particle.hoverOffsetY += (targetOffsetY - particle.hoverOffsetY) * 0.08;

    particle.x += CONFIG.driftX;
    particle.y += CONFIG.driftY;
    particle.x += particle.hoverOffsetX - previousOffsetX;
    particle.y += particle.hoverOffsetY - previousOffsetY;

    if (particle.x < 0) particle.x = width;
    if (particle.x > width) particle.x = 0;
    if (particle.y > height) particle.y = 0;
    if (particle.y < 0) particle.y = height;

    particle.twinklePhase += CONFIG.twinkleSpeed;
  };

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) {
      console.error('Canvas not found');
      return;
    }

    console.log('Initializing glitter background');

    // Get 2d context
    const ctx = canvas.getContext('2d', { willReadFrequently: false });
    if (!ctx) {
      console.error('Could not get canvas context');
      return;
    }

    // Set canvas size
    canvas.width = window.innerWidth;
    canvas.height = window.innerHeight;

    console.log(`Canvas size: ${canvas.width}x${canvas.height}`);

    // Create particles
    particlesRef.current = createParticles(canvas);
    console.log(`Created ${particlesRef.current.length} particles`);

    // Handle mouse
    const handleMouseMove = (e) => {
      mouseRef.current.x = e.clientX;
      mouseRef.current.y = e.clientY;
      mouseRef.current.active = true;
    };

    const handleMouseLeave = () => {
      mouseRef.current.active = false;
    };

    const handleResize = () => {
      canvas.width = window.innerWidth;
      canvas.height = window.innerHeight;
    };

    // Animation loop
    const animate = () => {
      // Clear canvas
      ctx.clearRect(0, 0, canvas.width, canvas.height);

      // Update and draw particles
      particlesRef.current.forEach((particle) => {
        updateParticle(
          particle,
          mouseRef.current.x,
          mouseRef.current.y,
          canvas.width,
          canvas.height,
        );
        drawSparkle(ctx, particle);
      });

      animationFrameRef.current = requestAnimationFrame(animate);
    };

    // Start animation
    animate();

    // Add event listeners
    window.addEventListener('mousemove', handleMouseMove);
    window.addEventListener('mouseleave', handleMouseLeave);
    window.addEventListener('resize', handleResize);

    // Cleanup
    return () => {
      window.removeEventListener('mousemove', handleMouseMove);
      window.removeEventListener('mouseleave', handleMouseLeave);
      window.removeEventListener('resize', handleResize);
      if (animationFrameRef.current) {
        cancelAnimationFrame(animationFrameRef.current);
      }
    };
  }, []);

  return <canvas ref={canvasRef} className="glitter-background" />;
};

export default GlitterBackground;
